package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * PORT-STUB(E4)
 *
 * 辉石冰附魔
 * <p>
 * 弓箭附魔，箭矢伤害转为魔法并施加冻伤
 * 箭矢命中时：
 * - 伤害转为魔法伤害
 * - 施加冻伤效果（持续10秒，效果等级 = 附魔等级 - 1）
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - 移除无效的 conflictsWith = {Enchantments.class}
 *   Enchantments 是原版附魔的持有者类（包含静态字段如 FLAMING_ARROWS），
 *   不是 Enchantment 的子类，checkCompatibility 中的 isInstance 检查永远不会匹配。
 *   与火矢的互斥已在 checkCompatibility 方法中正确实现。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentPyroxeneIce extends EnchantmentBase {
    private static final String VALUE_ID = "pyroxene_ice";
    private static final EnchantmentValues.Handle EFFECT_SECONDS = EnchantmentValues.define(VALUE_ID, "effect_seconds",
                    10, 1, 300);
    protected void onHurtAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
}

