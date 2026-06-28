package pers.roinflam.carianstyle.visual.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 光环显示注册表（纯客户端）。
 * <p>
 * 光环是否激活、半径多大、什么形状，全部由客户端根据“附近实体的同步装备 NBT”自行判断，
 * 因此无需任何网络包。每个光环登记：序列号、颜色、形状 {@link AuraShape}、探测器 {@link AuraDetector}。
 * <p>
 * <b>形状说明（关键）：</b>本系附魔的范围判定基本都是
 * {@code EntityUtil.getNearbyEntities(类, 实体, R)}，其底层是 {@code AABB.inflate(R)}——
 * 即一个从 -R 到 +R 的轴对齐立方体（俯视为边长 2R 的正方形），并非球形距离判定。
 * 因此这些光环的真实生效区是 {@link AuraShape#SQUARE}（半径 R 即正方形半边长）。
 * 若将来有附魔改用 {@code distanceTo <= R} 的球形判定，则用 {@link AuraShape#CIRCLE}。
 * <p>
 * <b>装备槽口径 {@link SlotScope}（修复护甲附魔“拿在手上也显示光环”的核心）：</b>
 * 探测器在判定附魔等级时，必须区分“看哪些装备槽”——护甲类常驻光环只应统计 4 件护甲槽，
 * 把主手/副手拿着的护甲算进去会导致光环误显示。为此提供三种口径：
 * <ul>
 *     <li>{@link SlotScope#ANY_MAX}：任意装备槽（含主手/副手/护甲）的最高等级——
 *         仅适用于“装在哪个槽都能生效”的附魔（如装在盾上、举盾触发的圣域）；</li>
 *     <li>{@link SlotScope#ARMOR_MAX}：仅 4 件护甲槽的最高等级——护甲常驻光环用此口径，
 *         护甲拿在手上时护甲槽为空，自然不再误触发；</li>
 *     <li>{@link SlotScope#ARMOR_SUM}：仅 4 件护甲槽的等级之和——护甲套装叠加型用此口径
 *         （与游戏内 {@code for(armor) totalLevel += getItemEnchantmentLevel} 口径一致）。</li>
 * </ul>
 * <p>
 * 内置探测器工厂覆盖：装备常驻（任意槽最高）、护甲常驻（护甲槽最高）、护甲叠加（护甲槽之和）、举盾触发。
 * 计时型/状态型光环（如重力场）无法仅凭客户端状态判断，需另走服务端同步，暂不在此。
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
public final class AuraDisplayRegistry {

    /**
     * 光环形状。
     * <ul>
     *     <li>{@link #SQUARE}：轴对齐正方形，半径 = 半边长（对应 {@code AABB.inflate(R)} 的盒形判定）；</li>
     *     <li>{@link #CIRCLE}：圆形，半径 = 圆半径（对应球形距离判定）。</li>
     * </ul>
     */
    public enum AuraShape {
        SQUARE,
        CIRCLE
    }

    /**
     * 附魔等级统计的装备槽口径。
     * <p>决定探测器在“判断该实体是否激活此光环、半径多大”时，统计哪些装备槽上的附魔等级。
     * 详见类注释中的口径说明。
     */
    public enum SlotScope {
        /** 任意装备槽（含主手/副手/护甲）的最高等级 */
        ANY_MAX,
        /** 仅 4 件护甲槽的最高等级（护甲常驻光环用，避免护甲拿手上误触发） */
        ARMOR_MAX,
        /** 仅 4 件护甲槽的等级之和（护甲套装叠加型用） */
        ARMOR_SUM
    }

    /** 光环探测器：给定实体与扫描上下文，返回光环半径（格）；<=0 表示该实体当前未激活此光环。 */
    @FunctionalInterface
    public interface AuraDetector {
        /**
         * @param entity 被检测实体
         * @param ctx    扫描上下文（缓存了该实体的装备附魔等级）
         * @return 半径（格），<=0 表示未激活
         */
        double getRadius(LivingEntity entity, ScanContext ctx);
    }

    /** 半径函数：由附魔等级算半径。 */
    @FunctionalInterface
    public interface RadiusFunction {
        /**
         * @param level 附魔等级
         * @return 半径（格）
         */
        double radius(int level);
    }

    /**
     * 一条光环注册项。
     *
     * @param serialId 序列号
     * @param color    颜色（0xRRGGBB）
     * @param shape    形状
     * @param detector 探测器
     */
    public record AuraInfo(int serialId, int color, AuraShape shape, AuraDetector detector) {
    }

    /** 全部注册项 */
    private static final List<AuraInfo> AURAS = new ArrayList<>();

    private AuraDisplayRegistry() {
    }

    /**
     * 注册一个光环显示项。
     *
     * @param serialId 序列号（唯一）
     * @param color    颜色（0xRRGGBB）
     * @param shape    形状
     * @param detector 探测器
     */
    public static void register(int serialId, int color, AuraShape shape, AuraDetector detector) {
        AURAS.add(new AuraInfo(serialId, color, shape, detector));
    }

    /**
     * @return 全部光环注册项（只读）
     */
    public static List<AuraInfo> getAuras() {
        return AURAS;
    }

    // ===================== 探测器工厂 =====================

    /**
     * 固定半径的装备常驻光环（<b>任意装备槽</b>存在该附魔即激活，取最高等级）。
     * <p><b>注意：</b>此工厂会把主手/副手拿着的物品也算进去，仅适用于“装在哪个槽都能生效”的附魔。
     * 护甲类附魔请改用 {@link #fixedArmor}，否则护甲拿在手上也会显示光环。
     *
     * @param enchantId 附魔注册 id（命名空间默认 mod id，如 "realm_of_magic"）
     * @param radius    固定半径
     * @return 探测器
     */
    public static AuraDetector fixed(String enchantId, double radius) {
        return new EnchantAuraDetector(enchantId, level -> radius, SlotScope.ANY_MAX, false);
    }

    /**
     * 固定半径的<b>护甲常驻</b>光环（仅 4 件护甲槽存在该附魔即激活，取护甲槽最高等级）。
     * <p>护甲拿在手上时护甲槽为空，故不会误触发——这是修复“护甲附魔拿手上也显示光环”的关键工厂。
     *
     * @param enchantId 附魔注册 id
     * @param radius    固定半径
     * @return 探测器
     */
    public static AuraDetector fixedArmor(String enchantId, double radius) {
        return new EnchantAuraDetector(enchantId, level -> radius, SlotScope.ARMOR_MAX, false);
    }

    /**
     * 半径随“任意装备槽最高等级”缩放的装备常驻光环。
     * <p>同 {@link #fixed}，会统计主手/副手；护甲缩放型请改用 {@link #scaledArmor}。
     *
     * @param enchantId 附魔注册 id
     * @param fn        半径函数
     * @return 探测器
     */
    public static AuraDetector scaled(String enchantId, RadiusFunction fn) {
        return new EnchantAuraDetector(enchantId, fn, SlotScope.ANY_MAX, false);
    }

    /**
     * 半径随“护甲槽最高等级”缩放的护甲常驻光环（仅统计 4 件护甲槽的最高等级）。
     *
     * @param enchantId 附魔注册 id
     * @param fn        半径函数（入参为护甲槽最高等级）
     * @return 探测器
     */
    public static AuraDetector scaledArmor(String enchantId, RadiusFunction fn) {
        return new EnchantAuraDetector(enchantId, fn, SlotScope.ARMOR_MAX, false);
    }

    /**
     * 半径随“4 件护甲等级之和”缩放的装备常驻光环（用于护甲套装叠加型，如回归性原理）。
     * <p>与游戏内 {@code for(armor) totalLevel += getItemEnchantmentLevel} 的口径一致。
     *
     * @param enchantId 附魔注册 id
     * @param fn        半径函数（入参为护甲等级之和）
     * @return 探测器
     */
    public static AuraDetector scaledArmorSum(String enchantId, RadiusFunction fn) {
        return new EnchantAuraDetector(enchantId, fn, SlotScope.ARMOR_SUM, false);
    }

    /**
     * 举盾时才激活的光环（如圣域）。半径固定，按<b>任意装备槽</b>最高等级判定是否激活。
     * <p>圣域为盾牌附魔（装在盾上、举盾触发），故采用任意槽口径；若将来圣域改为护甲附魔，
     * 应另提供一个 ARMOR_MAX + requireBlocking 的变体。
     *
     * @param enchantId 附魔注册 id
     * @param radius    固定半径
     * @return 探测器
     */
    public static AuraDetector blocking(String enchantId, double radius) {
        return new EnchantAuraDetector(enchantId, level -> radius, SlotScope.ANY_MAX, true);
    }

    /**
     * 基于“装备附魔等级”的通用探测器实现。
     * <p>
     * 用标准 Forge 注册表按 {@code carianstyle:<id>} 解析附魔，不依赖自定义注册类。
     */
    private static final class EnchantAuraDetector implements AuraDetector {

        private final String enchantId;
        private final RadiusFunction radiusFunction;
        private final boolean requireBlocking;
        /** 附魔等级的统计口径（任意槽最高 / 护甲槽最高 / 护甲槽之和） */
        private final SlotScope scope;

        /** 解析后的附魔对象缓存（首次解析成功后固定） */
        private Enchantment cached;
        private boolean resolved;

        EnchantAuraDetector(String enchantId, RadiusFunction radiusFunction,
                            SlotScope scope, boolean requireBlocking) {
            this.enchantId = enchantId;
            this.radiusFunction = radiusFunction;
            this.scope = scope;
            this.requireBlocking = requireBlocking;
        }

        /**
         * 懒解析附魔对象（注册表在 mod 加载后才可用，故首次调用时解析）。
         *
         * @return 附魔对象，未注册时为 null
         */
        private Enchantment resolve() {
            if (!resolved) {
                cached = ForgeRegistries.ENCHANTMENTS.getValue(new ResourceLocation(Reference.MOD_ID, enchantId));
                // 仅在成功解析后才标记完成，否则下次重试
                resolved = (cached != null);
            }
            return cached;
        }

        @Override
        public double getRadius(LivingEntity entity, ScanContext ctx) {
            Enchantment ench = resolve();
            if (ench == null) {
                return -1;
            }
            // 按口径取等级：护甲常驻只看护甲槽，从根上避免“护甲拿手上”误触发
            int level = switch (scope) {
                case ANY_MAX -> ctx.getLevel(ench);
                case ARMOR_MAX -> ctx.getArmorLevel(ench);
                case ARMOR_SUM -> ctx.getArmorSum(ench);
            };
            if (level <= 0) {
                return -1;
            }
            if (requireBlocking && !entity.isBlocking()) {
                return -1;
            }
            return radiusFunction.radius(level);
        }
    }

    // ===================== 扫描上下文 =====================

    /**
     * 扫描上下文：一次性收集某实体所有装备槽上的附魔等级，供同一实体的多个探测器复用。
     * <p>
     * 同时维护三套数据：
     * <ul>
     *     <li>{@code levels}：任意槽位的最高等级（用于装在哪个槽都生效的附魔）；</li>
     *     <li>{@code armorMaxLevels}：仅 4 件护甲槽的最高等级（用于护甲常驻型的激活判定）；</li>
     *     <li>{@code armorLevels}：仅 4 件护甲槽的等级之和（用于护甲套装叠加型的半径计算）。</li>
     * </ul>
     */
    public static final class ScanContext {

        /**
         * 缓存的装备槽数组。
         * <p><b>性能（视觉/行为零变化）：</b>{@link EquipmentSlot#values()} 每次调用都会克隆一份新数组，
         * 而本上下文对扫描范围内<b>每个</b>带附魔装备的实体都会构建一次并遍历全部槽位；
         * 缓存为静态常量可避免逐实体的数组分配。枚举值不可变、外部不修改，共享安全。
         */
        private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();

        /** 任意槽位最高等级 */
        private final Map<Enchantment, Integer> levels = new HashMap<>();
        /** 4 件护甲槽最高等级（护甲常驻光环判定用） */
        private final Map<Enchantment, Integer> armorMaxLevels = new HashMap<>();
        /** 4 件护甲槽等级之和（护甲套装叠加型用） */
        private final Map<Enchantment, Integer> armorLevels = new HashMap<>();

        /**
         * @param entity 被检测实体
         */
        public ScanContext(LivingEntity entity) {
            // 使用缓存的 EQUIPMENT_SLOTS，避免每次 EquipmentSlot.values() 克隆数组
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                boolean isArmor = slot.getType() == EquipmentSlot.Type.ARMOR;
                for (Map.Entry<Enchantment, Integer> e : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                    levels.merge(e.getKey(), e.getValue(), Math::max);
                    if (isArmor) {
                        // 仅护甲槽：同时维护“最高等级”与“等级之和”两套口径
                        armorMaxLevels.merge(e.getKey(), e.getValue(), Math::max);
                        armorLevels.merge(e.getKey(), e.getValue(), Integer::sum);
                    }
                }
            }
        }

        /**
         * @param ench 附魔
         * @return 该实体任意装备槽上的最高等级；不存在为 0
         */
        public int getLevel(Enchantment ench) {
            return ench == null ? 0 : levels.getOrDefault(ench, 0);
        }

        /**
         * @param ench 附魔
         * @return 该实体 4 件护甲槽上的最高等级；不存在为 0
         */
        public int getArmorLevel(Enchantment ench) {
            return ench == null ? 0 : armorMaxLevels.getOrDefault(ench, 0);
        }

        /**
         * @param ench 附魔
         * @return 该实体 4 件护甲上的等级之和；不存在为 0
         */
        public int getArmorSum(Enchantment ench) {
            return ench == null ? 0 : armorLevels.getOrDefault(ench, 0);
        }
    }
}
