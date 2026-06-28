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
 *     <li><b>不可能激活任何光环的实体直接跳过</b>（廉价的 NBT 标签存在性检查）：仅当实体
 *         「护甲槽带附魔」或「举盾且手持带附魔」时才构建昂贵的全槽位附魔等级表，
 *         详见 {@link #mayHaveAura(LivingEntity)}。这样仅主手拿着附魔武器、未举盾的玩家
 *         （很常见）会被直接略过，不再白白构建 ScanContext；</li>
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
     * 护甲装备槽数组（头/胸/腿/脚）。
     * <p><b>性能（视觉/行为零变化）：</b>快速过滤 {@link #hasEnchantedArmor(LivingEntity)} 处于扫描
     * 热路径、会对范围内<b>每个</b>实体调用一次。直接列举为静态常量，既避免
     * {@link EquipmentSlot#values()} 每次克隆数组，又只遍历 4 个护甲槽而非全部 6 个槽位。
     * 注意：枚举值不可变、外部也不会修改本数组，故共享安全。
     */
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

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
            // 性能：先做一次廉价过滤跳过「不可能激活任何光环」的实体（详见 mayHaveAura），
            // 避免为成片普通怪物、或仅主手持武器未举盾的玩家构建昂贵的 ScanContext。
            if (!mayHaveAura(entity)) {
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
     * 快速判断实体是否<b>可能</b>激活某个已注册光环。
     * <p>仅做廉价的 NBT 标签存在性检查（{@link ItemStack#isEnchanted()}，不解析具体附魔），
     * 开销远小于 {@link AuraDisplayRegistry.ScanContext} 构建时的「全槽位逐附魔解析 + 入表」。
     * 返回 true 才构建上下文交由各探测器精确判定。
     * <p>
     * <b>口径必须覆盖所有已注册光环的 {@link AuraDisplayRegistry.SlotScope}：</b>
     * <ul>
     *     <li>护甲类光环（ARMOR_MAX / ARMOR_SUM）→ 由 {@link #hasEnchantedArmor} 覆盖；</li>
     *     <li>手持类光环（当前仅圣域 holy_ground，ANY_MAX 且要求举盾）→ 由
     *         {@link #isBlockingWithEnchantedHand} 覆盖。</li>
     * </ul>
     * <b>⚠ 耦合警告：</b>若将来新增「不要求举盾的手持/任意槽光环」，必须在此扩展过滤条件，
     * 否则该光环会被错误略过、永不显示。
     *
     * @param entity 待检测实体
     * @return 可能激活某光环返回 true
     */
    private static boolean mayHaveAura(LivingEntity entity) {
        return hasEnchantedArmor(entity) || isBlockingWithEnchantedHand(entity);
    }

    /**
     * 快速判断实体 4 件护甲槽中是否至少有一件带附魔。
     * <p>覆盖所有护甲类光环（realm_of_magic / topps_stand 的 ARMOR_MAX、
     * regressive_principle 的 ARMOR_SUM）的激活前提。
     *
     * @param entity 待检测实体
     * @return 任一护甲槽存在带附魔物品返回 true
     */
    private static boolean hasEnchantedArmor(LivingEntity entity) {
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isEnchanted()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 快速判断实体是否正在举盾、且主手或副手持有带附魔物品。
     * <p>覆盖圣域 holy_ground（盾牌附魔，装在盾上、举盾触发）这类「手持 + 举盾」光环：
     * 举盾时盾位于手持槽，故只要手持物带附魔即放行，交由探测器精确判定；
     * 未举盾时这类光环本就不激活，无需放行。
     *
     * @param entity 待检测实体
     * @return 正在举盾且手持带附魔物品返回 true
     */
    private static boolean isBlockingWithEnchantedHand(LivingEntity entity) {
        if (!entity.isBlocking()) {
            return false;
        }
        ItemStack main = entity.getItemBySlot(EquipmentSlot.MAINHAND);
        if (!main.isEmpty() && main.isEnchanted()) {
            return true;
        }
        ItemStack off = entity.getItemBySlot(EquipmentSlot.OFFHAND);
        return !off.isEmpty() && off.isEnchanted();
    }

    /**
     * @return 当前激活光环列表（只读）
     */
    public static List<ActiveAura> getActiveAuras() {
        return active;
    }
}
