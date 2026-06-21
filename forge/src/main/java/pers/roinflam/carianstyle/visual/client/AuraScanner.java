package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.StackHudManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 光环客户端扫描器：定期扫描玩家附近的生物，判断各自激活了哪些光环，
 * 产出供 {@link AuraGroundRenderer} 渲染的列表。
 * <p>
 * 性能要点：
 * <ul>
 *     <li>每 {@link #SCAN_INTERVAL} tick 才扫描一次（光环出现/消失有 ≤0.5 秒延迟，视觉无碍）；</li>
 *     <li>扫描范围限定 {@link #SCAN_RANGE} 格，且<b>与 AuraGroundRenderer 的 RENDER_CULL 取同值</b>，
 *         避免「扫描产出范围 ≠ 渲染裁剪范围」造成的浪费；</li>
 *     <li><b>无附魔装备的实体直接跳过</b>（廉价的 NBT 标签存在性检查），不再为成片普通怪物
 *         构建昂贵的全槽位附魔等级表，详见 {@link #hasEnchantedEquipment(LivingEntity)}；</li>
 *     <li>每个实体只构建一次附魔等级表（{@link AuraDisplayRegistry.ScanContext}），多探测器复用；</li>
 *     <li>结果存 volatile 列表，渲染线程直接读。</li>
 * </ul>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AuraScanner {

    /**
     * 一个已激活的光环实例。
     *
     * @param entityId 实体网络 id（渲染时反查实体取实时坐标）
     * @param serialId 光环序列号（渲染端据此做逐光环的出现/旋转动画状态键）
     * @param color    颜色（0xRRGGBB）
     * @param radius   半径（格）；方形时为半边长，圆形时为圆半径
     * @param shape    形状（方形/圆形）
     */
    public record ActiveAura(int entityId, int serialId, int color, double radius,
                             AuraDisplayRegistry.AuraShape shape) {
    }

    /** 扫描间隔（tick） */
    private static final int SCAN_INTERVAL = 10;
    /**
     * 扫描半径（格）。
     * <p><b>应与 AuraGroundRenderer 的 RENDER_CULL 保持一致</b>：扫描器只产出该范围内实体的光环，
     * 二者取同值可避免「渲染裁剪比扫描范围大、那段渲染能力拿不到数据」的浪费。
     * 当前最大光环（圣域）仅 16 格，48 格已相当宽裕。
     */
    private static final double SCAN_RANGE = 48.0;

    /**
     * 缓存的装备槽数组。
     * <p><b>性能（视觉/行为零变化）：</b>{@link EquipmentSlot#values()} 每次调用都会克隆一份新数组
     * （Java 枚举 {@code values()} 的固有行为），而 {@link #hasEnchantedEquipment(LivingEntity)}
     * 处于扫描热路径、会对范围内<b>每个</b>实体调用一次。缓存为静态常量后，逐实体检查不再产生
     * 数组分配，直接降低 GC 压力。注意：枚举值不可变，外部也不会修改本数组，故共享安全。
     */
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

    /** tick 计数 */
    private static int counter = 0;
    /** 当前激活光环列表（volatile：扫描写、渲染读） */
    private static volatile List<ActiveAura> active = Collections.emptyList();

    private AuraScanner() {
    }

    /**
     * 客户端 tick 末尾扫描。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            // 离开世界：清空光环与叠层 HUD，避免残留
            active = Collections.emptyList();
            StackHudManager.clear();
            return;
        }
        if (++counter < SCAN_INTERVAL) {
            return;
        }
        counter = 0;
        scan(mc);
    }

    /**
     * 执行一次扫描。
     *
     * @param mc Minecraft 实例
     */
    private static void scan(Minecraft mc) {
        List<AuraDisplayRegistry.AuraInfo> auras = AuraDisplayRegistry.getAuras();
        if (auras.isEmpty()) {
            active = Collections.emptyList();
            return;
        }
        AABB box = mc.player.getBoundingBox().inflate(SCAN_RANGE);
        List<LivingEntity> entities = mc.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
        if (entities.isEmpty()) {
            active = Collections.emptyList();
            return;
        }
        List<ActiveAura> result = new ArrayList<>();
        for (LivingEntity entity : entities) {
            // 性能：无任何带附魔装备的实体绝不可能激活光环（所有探测器都要求附魔等级>0），
            // 先做一次廉价的 NBT 标签检查跳过它们，避免为成片普通怪物构建昂贵的 ScanContext。
            if (!hasEnchantedEquipment(entity)) {
                continue;
            }
            AuraDisplayRegistry.ScanContext ctx = new AuraDisplayRegistry.ScanContext(entity);
            for (AuraDisplayRegistry.AuraInfo info : auras) {
                double radius = info.detector().getRadius(entity, ctx);
                if (radius > 0) {
                    result.add(new ActiveAura(entity.getId(), info.serialId(), info.color(), radius, info.shape()));
                }
            }
        }
        active = result;
    }

    /**
     * 快速判断实体是否至少有一件带附魔的装备。
     * <p>仅检查各装备槽物品的 NBT 是否含非空 {@code Enchantments} 标签
     * （{@link ItemStack#isEnchanted()}，不解析具体附魔），开销远小于
     * {@link AuraDisplayRegistry.ScanContext} 构建时的「全槽位逐附魔解析 + 入表」。
     * <p>行为等价性：所有光环探测器都要求对应附魔等级 &gt; 0，而无任何附魔装备的实体
     * 不可能满足，故跳过它们不会改变扫描结果。
     *
     * @param entity 待检测实体
     * @return 任一装备槽存在带附魔物品返回 true
     */
    private static boolean hasEnchantedEquipment(LivingEntity entity) {
        // 使用缓存的 EQUIPMENT_SLOTS，避免每次 EquipmentSlot.values() 克隆数组
        for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isEnchanted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return 当前激活光环列表（只读）
     */
    public static List<ActiveAura> getActiveAuras() {
        return active;
    }
}
