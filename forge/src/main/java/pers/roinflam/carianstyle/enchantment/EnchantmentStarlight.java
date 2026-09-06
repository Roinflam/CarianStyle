package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星光附魔 - 优化: LivingTickEvent -&gt; PlayerTickEvent
 *
 * <h3>v2.2 性能优化：装备扫描缓存 + 方块更新标志</h3>
 *
 * <p><b>优化前的开销。</b>本附魔挂在 {@code PlayerTickEvent} 上，
 * 服务器里<b>每个玩家每 tick</b>都会执行：</p>
 * <ol>
 *   <li>遍历 4 个护甲槽，对每一件调用
 *       {@code EnchantmentHelper.getItemEnchantmentLevel}——
 *       该方法会<b>反序列化物品的附魔 ListTag</b>，是实打实的 NBT 解析；</li>
 *   <li>然后才判断玩家有没有这个附魔。</li>
 * </ol>
 * <p>
 * 顺序反了：<b>绝大多数玩家根本没有星光附魔</b>，却每人每 tick 白付 4 次 NBT 解析。
 * 50 人服务器就是 200 次/tick、4000 次/秒，而其中可能一次都没命中。
 * </p>
 *
 * <p><b>做法。</b>为每个玩家缓存「是否装备了星光」这个布尔值，
 * 每 {@link #RESCAN_INTERVAL} tick（1 秒）才重新扫描一次护甲槽。
 * 没有该附魔的玩家每 tick 的开销降为「一次 Map 查询 + 一次计数器自增」，
 * NBT 解析减少 95%。</p>
 * <p>
 * <b>为什么 1 秒的延迟可以接受：</b>缓存过期后最坏情况是玩家换上带星光的护甲后
 * 最多 1 秒才开始发光、脱下后最多 1 秒才停止。这个效果本身是「走到哪亮到哪」的
 * 环境光，晚一秒亮起完全无法察觉——不像伤害类附魔那样需要即时生效。
 * </p>
 * <p>
 * <b>为什么不用 {@code EnchantmentEventHandler} 里那套装备哈希缓存：</b>
 * 那套缓存服务的是「走模板方法分发」的附魔，本附魔是独立 {@code @SubscribeEvent}，
 * 接不进去。把它们统一收口是后续批次的工作，本次只做局部优化，不动那边的结构。
 * </p>
 *
 * <p><b>另一处改动：{@code setBlock} 的更新标志由 3 改为 {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}。</b></p>
 * <p>
 * 标志 3 = {@code UPDATE_NEIGHBORS(1) | UPDATE_CLIENTS(2)}，其中
 * {@code UPDATE_NEIGHBORS} 会通知六个相邻方块「你旁边变了」。
 * 而本方块是个 {@code AirBlock} 子类的纯光源，<b>没有任何方块需要对它作出反应</b>。
 * </p>
 * <p>
 * 更要紧的是：玩家移动时本附魔<b>每 tick 放一个新光源方块</b>，
 * 也就是每秒 20 次向周围广播方块更新。如果玩家从一片红石电路旁边跑过去，
 * 这 20 次/秒的邻居通知会真实地触发红石重算。去掉这个标志后行为不变、副作用消失。
 * </p>
 * <p>
 * 保留 {@code UPDATE_CLIENTS}（客户端必须收到才能看见光），
 * 追加 {@code UPDATE_KNOWN_SHAPE} 跳过形状更新（空气方块没有形状可传播）。
 * </p>
 *
 * <p><b>不能做的优化：整体降频。</b>
 * {@code MoveLight} 的 BlockEntity 在存在超过 1 tick 后会自我移除，
 * 靠的是每 tick 的 {@code retime()} 续命。若把整个方法降频到 N tick 一次，
 * 光源会变成「亮 2 tick、灭 N-2 tick」的闪烁。因此方块侧的逻辑必须保持每 tick 执行，
 * 只能优化它<b>前面</b>那段判定。</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "starlight", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET})
@Mod.EventBusSubscriber
public class EnchantmentStarlight extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.starlight.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "starlight";

    /**
     * 护甲扫描缓存的刷新间隔（tick）；调大更省性能，代价是换装后生效更慢
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle RESCAN_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "rescan_interval",
                    20, 1, 200);


    /**
     * 护甲扫描的重新检测间隔（tick）。
     * <p>20 tick = 1 秒。见类注释关于延迟可接受性的说明。</p>
     */

    /**
     * 放置光源方块时使用的更新标志。
     * <p>{@code UPDATE_CLIENTS} 让客户端看见光；{@code UPDATE_KNOWN_SHAPE} 跳过形状传播。
     * 刻意<b>不含</b> {@code UPDATE_NEIGHBORS}，理由见类注释。</p>
     */
    private static final int LIGHT_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    /**
     * 玩家装备扫描缓存。
     * <p>
     * 用 {@link ConcurrentHashMap} 而非普通 HashMap：本 Map 在服务端 tick 线程写入、
     * 在玩家登出事件里移除，两者在专用服务器上同属主线程，但 Mohist 这类混合端
     * 存在插件从其它线程触发登出事件的可能，用并发容器成本极低而更稳妥。
     * </p>
     */
    private static final Map<UUID, ArmorScanCache> SCAN_CACHE = new ConcurrentHashMap<>();

    public EnchantmentStarlight() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET});
    }

    /**
     * 单个玩家的护甲扫描结果缓存。
     */
    private static final class ArmorScanCache {

        /** 上次扫描的结论：玩家是否装备了星光 */
        private boolean equipped;

        /**
         * 距上次扫描过去了多少 tick。
         * <p>初始值设为 {@link #RESCAN_INTERVAL}，使玩家首次进入 tick 时立即扫描一次，
         * 而不是先按「未装备」处理一秒。</p>
         */
        private int ticksSinceScan = RESCAN_INTERVAL.getInt();

        /**
         * 取缓存结果，必要时重新扫描。
         *
         * @param player    玩家
         * @param starlight 星光附魔实例
         * @return 玩家是否装备了星光
         */
        private boolean isEquipped(@NotNull Player player, @NotNull Enchantment starlight) {
            ticksSinceScan++;
            if (ticksSinceScan < RESCAN_INTERVAL.getInt()) {
                return equipped;
            }
            ticksSinceScan = 0;

            int totalLevel = 0;
            for (ItemStack armor : player.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    totalLevel += EnchantmentHelper.getItemEnchantmentLevel(starlight, armor);
                }
            }
            equipped = totalLevel > 0;
            return equipped;
        }
    }

    /**
     * 每 tick 在玩家脚下放置/续期一个隐形光源方块。
     * <p>优化：从LivingTickEvent改为PlayerTickEvent，怪物不触发。</p>
     * <p>v2.2：护甲扫描结果缓存 1 秒，未装备该附魔的玩家几乎零开销。</p>
     *
     * @param evt 玩家 tick 事件
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;

        Player entity = evt.player;
        Enchantment starlight = EnchantmentRegistry.getEnchantmentByClass(EnchantmentStarlight.class);
        if (starlight == null) return;

        // ⭐ v2.2：先查缓存再决定要不要解析 NBT。
        // computeIfAbsent 只在玩家首次 tick 时创建条目，之后是一次 Map 查询。
        ArmorScanCache cache = SCAN_CACHE.computeIfAbsent(entity.getUUID(), uuid -> new ArmorScanCache());
        if (!cache.isEquipped(entity, starlight)) return;

        Level world = entity.level();
        int blockX = Mth.floor(entity.getX());
        int blockY = Mth.floor(entity.getY() - 0.2D);
        int blockZ = Mth.floor(entity.getZ());
        BlockPos blockPos = new BlockPos(blockX, blockY + 1, blockZ);
        if (!world.isEmptyBlock(blockPos)) return;
        if (world.getBlockEntity(blockPos) instanceof MoveLight moveLight) { moveLight.retime(); return; }
        else if (world.getBlockState(blockPos).getBlock() instanceof HideLight) { world.removeBlock(blockPos, false); }
        world.setBlock(blockPos, CarianStyleBlocks.HIDE_LIGHT.get().defaultBlockState(), LIGHT_BLOCK_FLAGS);
    }

    /**
     * 玩家登出时清理缓存条目，防止长期运行的服务器上 Map 无界增长。
     * <p>
     * 条目本身只有一个 boolean + 一个 int，单条内存可忽略；但一个开了几个月的服务器
     * 会积累掉所有历史登录过的玩家 UUID，属于典型的「慢泄漏」。
     * </p>
     *
     * @param evt 玩家登出事件
     */
    @SubscribeEvent
    public static void onPlayerLoggedOut(@NotNull PlayerEvent.PlayerLoggedOutEvent evt) {
        SCAN_CACHE.remove(evt.getEntity().getUUID());
    }

    @Override public int getMinCost(int l) { return (int)((23 + (l - 1) * 9) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
