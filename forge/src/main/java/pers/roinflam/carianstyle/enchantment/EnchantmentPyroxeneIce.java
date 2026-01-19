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
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 辉石冰附魔
 * <p>
 * 弓箭附魔，箭矢伤害转为魔法并施加冻伤
 * 箭矢命中时：
 * - 伤害转为魔法伤害
 * - 施加冻伤效果（持续10秒，效果等级 = 附魔等级 - 1）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "pyroxene_ice",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {Enchantments.class}
)
public class EnchantmentPyroxeneIce extends EnchantmentBase {

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
                10 * 20,
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
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.FLAMING_ARROWS);
    }
}