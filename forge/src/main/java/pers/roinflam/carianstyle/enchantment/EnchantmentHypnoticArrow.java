package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 催眠箭附魔
 *
 * 弓箭附魔，概率使目标入睡
 * 箭矢命中时：
 * - 3% × 等级的概率触发
 * - 延迟5tick后施加睡眠效果（持续 = 等级 × 3秒，效果等级 = 附魔等级 - 1）
 */
@AutoRegisterEnchantment(
        id = "hypnotic_arrow",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentHypnoticArrow extends EnchantmentBase {

    public EnchantmentHypnoticArrow() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时概率使目标入睡
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        // 必须是箭矢伤害
        if (damageSource == null || !(damageSource.getImmediateSource() instanceof EntityArrow)) {
            return;
        }

        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 3% × 等级的概率触发
        if (!RandomUtil.percentageChance(level * 3)) {
            return;
        }

        // 延迟5tick后施加睡眠效果
        new SynchronizationTask(5) {
            @Override
            public void run() {
                victim.addPotionEffect(new PotionEffect(
                        CarianStylePotion.SLEEP,
                        level * 3 * 20,
                        level - 1
                ));
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class));
    }
}