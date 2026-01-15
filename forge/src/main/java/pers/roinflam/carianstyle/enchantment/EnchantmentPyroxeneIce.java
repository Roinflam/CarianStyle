package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 辉石冰附魔
 *
 * 弓箭附魔，箭矢伤害转为魔法并施加冻伤
 * 箭矢命中时：
 * - 伤害转为魔法伤害
 * - 施加冻伤效果（持续10秒，效果等级 = 附魔等级 - 1）
 */
@AutoRegisterEnchantment(
        id = "pyroxene_ice",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentPyroxeneIce extends EnchantmentBase {

    public EnchantmentPyroxeneIce() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时转为魔法伤害并施加冻伤
     */
    @Override
    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        // 必须是箭矢伤害
        if (damageSource == null || !(damageSource.getImmediateSource() instanceof EntityArrow)) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 转为魔法伤害
        damageSource.setMagicDamage();

        // 施加冻伤效果
        victim.addPotionEffect(new PotionEffect(
                CarianStylePotion.FROSTBITE,
                10 * 20,
                level - 1
        ));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.FLAME);
    }
}