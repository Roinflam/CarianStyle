package pers.roinflam.carianstyle.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.EquipmentEnchantContext;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 叠层显示服务端管理器：定期轮询每个在线玩家的叠层读取器，
 * 与上次发送的快照做差量比对，仅在变化时把全量快照推给该玩家。
 * <p>
 * 性能要点：
 * <ul>
 *     <li>每 {@link #POLL_INTERVAL} tick 才轮询一次；</li>
 *     <li><b>每个玩家每轮只构建一次 {@link EquipmentEnchantContext}（装备附魔快照），
 *         全部读取器共用</b>——见下方 v2 优化说明；</li>
 *     <li>读取器都是廉价的 map/属性查询；</li>
 *     <li>差量门控：层数或上限任一变化才发包，否则不发；</li>
 *     <li>只发给“叠层所属的那名玩家”，不广播。</li>
 * </ul>
 * <p>
 * <b>v2 性能优化（显示与行为完全不变）：</b>
 * 优化前，每个叠层读取器都会自行调用 {@code EnchantmentHelper.getItemEnchantmentLevel}
 * 判断「主手 / 护甲是否带有该附魔」，而该方法内部要逐条遍历物品附魔 NBT 并为每条
 * {@code ResourceLocation.tryParse}；护甲类读取器还要再套一层 4 件护甲的循环。
 * 结果是同一件装备的 NBT 在一轮内被反复解析数十遍。
 * <p>
 * 现在改为在 {@link #pollAndSync} 开头为该玩家构建一次
 * {@link EquipmentEnchantContext}（主手 + 4 件护甲，至多 5 次 NBT 反序列化），
 * 再把它传给全部读取器，读取器内部的门控判断全部降为 {@link Map} 的 O(1) 查表。
 * 单玩家单轮的 NBT 遍历次数由数十次降为至多 5 次，人数越多收益越明显。
 *
 * <h3>v3 性能：轮询间隔由 3 tick 放宽到 {@value #POLL_INTERVAL} tick</h3>
 * <p>
 * v2 把<b>单次轮询的成本</b>压下去了，但<b>轮询频率</b>一直是最初设定的 3 tick。
 * 剩余成本是线性于频率的：
 * </p>
 * <pre>
 * 每秒读取器调用次数 = 在线人数 × 读取器数量 × (20 / POLL_INTERVAL)
 *
 * 60 人 × 22 个读取器 × (20 / 3) ≈ 每秒 26400 次
 * 60 人 × 22 个读取器 × (20 / 5) ≈ 每秒 15840 次
 * </pre>
 * <p>
 * 每次调用虽然已经是 O(1) 查表 + 少量 Map 读取，但 22 个读取器里有相当一部分还要
 * 走 {@code EnchantmentDataManager} 的计数器 / 冷却查询（内部要 {@code buildKey}
 * 拼一次字符串），乘以 60 人之后并非可以忽略的量。放宽到 5 tick 直接砍掉 40%。
 * </p>
 * <p>
 * <b>观感代价：</b>叠层数字与冷却秒数的刷新延迟由 0.15 秒变为 0.25 秒。
 * 这里要说清楚它<b>为什么可以接受</b>：
 * </p>
 * <ul>
 *     <li><b>冷却倒计时</b>（满月 / 死诞者 / 回溯 / 古龙雷击 / 巨剑阵 / 圣血罗妮亚 / 癫火蔓延）
 *         显示的是<b>整秒</b>数字，0.25 秒的刷新粒度对「还剩几秒」的读数完全无影响；</li>
 *     <li><b>叠层数字</b>（尸山血海、腐败翼剑、连击、居合蓄力等）在战斗中的变化速率
 *         受攻击速度限制，一次攻击间隔通常远大于 5 tick，因此几乎不会出现「打了一下数字没跳」；</li>
 *     <li>客户端 HUD 的进度条本就有 {@code SPEED_BAR} 指数平滑（见 {@code StackHudOverlay}），
 *         数值是<b>平滑趋近</b>目标而非瞬跳，轮询间隔的变化被这层平滑进一步吸收。</li>
 * </ul>
 * <p>
 * <b>如果你觉得手感变钝了，改回 3 即可</b>——本项是全部性能改动里唯一带观感取舍的一项，
 * 且回退成本是<b>一个常量</b>，不涉及任何逻辑。
 * </p>
 * <p>
 * <b>刻意没做的两件事：</b>
 * </p>
 * <ul>
 *     <li><b>没有把玩家分批到不同 tick 上轮询</b>（比如按 UUID 哈希错开）。那样能把峰值抹平，
 *         但会让 {@link #LAST_SENT} 的差量比对逻辑与「同一轮内所有玩家状态一致」的前提脱钩，
 *         调试难度上升，而收益只是削峰不是降总量；</li>
 *     <li><b>没有跳过「本轮装备没变化」的玩家</b>。听起来诱人，但叠层的来源大多<b>不是装备</b>
 *         （计数器、冷却、属性修正器都会在装备不变的情况下变化），
 *         装备快照只是<b>门控</b>用的，跳过会直接导致叠层数字卡住不动。</li>
 * </ul>
 *
 * @author FlameForge
 * @version 3
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class StackDisplayManager {

    /**
     * 轮询间隔（tick）。
     * <p>
     * v3：由 3 放宽到 {@value}（0.25 秒），读取器调用总量降低 40%。
     * 观感代价与取舍理由详见类注释的「v3 性能」小节——
     * <b>若嫌手感钝，改回 3 即可，不涉及任何其它逻辑。</b>
     * </p>
     */
    private static final int POLL_INTERVAL = 5;

    /** tick 计数 */
    private static int tickCounter = 0;

    /** 上次发给各玩家的快照：UUID -> (serialId -> 层数/上限)。仅服务端主线程访问。 */
    private static final Map<UUID, Map<Integer, StackDisplayRegistry.Stacks>> LAST_SENT = new HashMap<>();

    private StackDisplayManager() {
    }

    /**
     * 服务端 tick 末尾轮询。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (++tickCounter < POLL_INTERVAL) {
            return;
        }
        tickCounter = 0;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        List<StackDisplayRegistry.Entry> providers = StackDisplayRegistry.getEntries();
        if (providers.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            pollAndSync(player, providers);
        }
    }

    /**
     * 轮询单个玩家并按需同步。
     * <p>
     * v2：开头构建一次装备附魔快照并传给所有读取器，避免每个读取器重复解析装备 NBT。
     * </p>
     *
     * @param player    玩家
     * @param providers 全部叠层读取器
     */
    private static void pollAndSync(ServerPlayer player, List<StackDisplayRegistry.Entry> providers) {
        // v2：本玩家本轮的装备附魔快照，供全部读取器共用（至多 5 次 NBT 反序列化）
        EquipmentEnchantContext equipmentContext = new EquipmentEnchantContext(player);

        Map<Integer, StackDisplayRegistry.Stacks> current = null;
        for (StackDisplayRegistry.Entry entry : providers) {
            StackDisplayRegistry.Stacks stacks;
            try {
                stacks = entry.provider().getStacks(player, equipmentContext);
            } catch (Exception ex) {
                // 单个读取器异常不应影响其他附魔显示
                stacks = StackDisplayRegistry.Stacks.NONE;
            }
            if (stacks == null || stacks.count() <= 0) {
                continue;
            }
            if (current == null) {
                current = new HashMap<>();
            }
            current.put(entry.serialId(), stacks);
        }
        if (current == null) {
            current = Collections.emptyMap();
        }

        UUID uuid = player.getUUID();
        Map<Integer, StackDisplayRegistry.Stacks> last = LAST_SENT.getOrDefault(uuid, Collections.emptyMap());
        if (current.equals(last)) {
            // 层数与上限均无变化，不发包（Stacks 为 record，equals 按值比较）
            return;
        }

        VisualNetwork.sendToPlayer(player, new StackDisplayPacket(current));

        if (current.isEmpty()) {
            LAST_SENT.remove(uuid);
        } else {
            LAST_SENT.put(uuid, new HashMap<>(current));
        }
    }

    /**
     * 玩家登出时清理其快照缓存。
     *
     * @param event 登出事件
     */
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }
}
