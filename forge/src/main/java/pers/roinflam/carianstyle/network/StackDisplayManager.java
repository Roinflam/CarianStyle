package pers.roinflam.carianstyle.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import pers.roinflam.carianstyle.utils.Reference;
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
 *     <li>读取器都是廉价的 map/属性查询；</li>
 *     <li>差量门控：层数或上限任一变化才发包，否则不发；</li>
 *     <li>只发给“叠层所属的那名玩家”，不广播。</li>
 * </ul>
 *
 * @author FlameForge
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
     *
     * @param player    玩家
     * @param providers 全部叠层读取器
     */
    private static void pollAndSync(ServerPlayer player, List<StackDisplayRegistry.Entry> providers) {
        Map<Integer, StackDisplayRegistry.Stacks> current = null;
        for (StackDisplayRegistry.Entry entry : providers) {
            StackDisplayRegistry.Stacks stacks;
            try {
                stacks = entry.provider().getStacks(player);
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
