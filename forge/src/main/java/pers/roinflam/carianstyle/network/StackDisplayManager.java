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
 * @author FlameForge
 * @version 2
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class StackDisplayManager {

    /** 轮询间隔（tick）。3 tick≈0.15 秒，对叠层数字足够跟手。 */
    private static final int POLL_INTERVAL = 3;

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
