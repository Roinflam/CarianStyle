package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
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
 * 催眠烟雾附魔
 *
 * 武器附魔，概率使目标入睡
 * 攻击时：
 * - 2% × 等级的概率触发
 * - 延迟5tick后施加睡眠效果（持续 = 等级 × 3秒，效果等级 = 附魔等级 - 1）
 */
@AutoRegisterEnchantment(
        id = "hypnotic_smoke",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentHypnoticSmoke extends EnchantmentBase {

    public EnchantmentHypnoticSmoke() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时概率使目标入睡
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 2% × 等级的概率触发
        if (!RandomUtil.percentageChance(level * 2)) {
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
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class));
    }
}