package pers.roinflam.carianstyle.visual.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.utils.Reference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.utils.util.EquipmentSlots;

/**
 * PORT-STUB(V1)
 *
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
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class AuraDisplayRegistry {

    public enum AuraShape {
;

        public static final AuraShape SQUARE = null; // PORT-STUB
        public static final AuraShape CIRCLE = null; // PORT-STUB
    }

    public enum SlotScope {
;

        public static final SlotScope ANY_MAX = null; // PORT-STUB
        public static final SlotScope ARMOR_MAX = null; // PORT-STUB
        public static final SlotScope ARMOR_SUM = null; // PORT-STUB
    }

    @FunctionalInterface
    public interface AuraDetector {
        double getRadius(LivingEntity entity, ScanContext ctx);
    }

    @FunctionalInterface
    public interface RadiusFunction {
        double radius(int level);
    }

    public record AuraInfo(int serialId, int color, AuraShape shape, AuraDetector detector) {
    }
    private static final List<AuraInfo> AURAS = new ArrayList<>();
    private AuraDisplayRegistry() {
    }
    public static void register(int serialId, int color, AuraShape shape, AuraDetector detector) {
    }
    public static List<AuraInfo> getAuras() {
        return java.util.List.of();
    }
    public static AuraDetector fixed(String enchantId, double radius) {
        return null;
    }
    public static AuraDetector fixedArmor(String enchantId, double radius) {
        return null;
    }
    public static AuraDetector scaled(String enchantId, RadiusFunction fn) {
        return null;
    }
    public static AuraDetector scaledArmor(String enchantId, RadiusFunction fn) {
        return null;
    }
    public static AuraDetector scaledArmorSum(String enchantId, RadiusFunction fn) {
        return null;
    }
    public static AuraDetector blocking(String enchantId, double radius) {
        return null;
    }

    private static final class EnchantAuraDetector implements AuraDetector {
        EnchantAuraDetector(String enchantId, RadiusFunction radiusFunction, SlotScope scope, boolean requireBlocking) {
        }
        public double getRadius(LivingEntity entity, ScanContext ctx) {
            return 0.0D;
        }
    }

    public static final class ScanContext {
        private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlots.SCAN;
        public ScanContext(LivingEntity entity) {
        }
        public int getLevel(Enchantment ench) {
            return 0;
        }
        public int getArmorLevel(Enchantment ench) {
            return 0;
        }
        public int getArmorSum(Enchantment ench) {
            return 0;
        }
    }
}

