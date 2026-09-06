package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
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
@AutoRegisterEnchantment(
        id = "pyroxene_ice",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
        // v2.1修复：移除 conflictsWith = {Enchantments.class}，该配置无效
        // 与火矢(FLAMING_ARROWS)的互斥通过下方 checkCompatibility 方法实现
)
public class EnchantmentPyroxeneIce extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.pyroxene_ice.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "pyroxene_ice";

    /**
     * 施加效果的持续秒数
     * <p>默认 10，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_SECONDS =
            EnchantmentValues.define(VALUE_ID, "effect_seconds",
                    10, 1, 300);

    public EnchantmentPyroxeneIce() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        // 必须是箭矢伤害
        if (damageSource == null || !(damageSource.getDirectEntity() instanceof AbstractArrow)) {
            return;
        }

        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 转为魔法伤害
        DamageSourceUtil.setMagicDamage(damageSource);

        // 施加冻伤效果
        victim.addEffect(new MobEffectInstance(
                CarianStylePotion.FROSTBITE.get(),
                EFFECT_SECONDS.getInt() * 20,
                level - 1
        ));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        // 与火矢互斥
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.FLAMING_ARROWS);
    }
}
