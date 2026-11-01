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
 * 满蓄力攻击时：
 * - 2% × 等级的概率触发
 * - 延迟5tick后施加睡眠效果（持续 = 等级 × 3秒，效果等级 = 附魔等级 - 1）
 * <p>
 * 以前不看蓄力，连点能把概率成倍放大。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "hypnotic_smoke",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentHypnoticSmoke extends EnchantmentBase {

    /** 每级触发概率（%） */
    private static final double CHANCE_PER_LEVEL = 2.0D;
    /** 每级睡眠秒数 */
    private static final int DURATION_SECONDS_PER_LEVEL = 3;
    /** 施加睡眠前的延迟（tick） */
    private static final int APPLY_DELAY = 5;

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

        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        // 2% × 等级的概率触发
        if (!RandomUtil.percentageChance(level * CHANCE_PER_LEVEL)) {
            return;
        }

        // 延迟5tick后施加睡眠效果
        new SynchronizationTask(APPLY_DELAY) {
            @Override
            public void run() {
                victim.addPotionEffect(new PotionEffect(
                        CarianStylePotion.SLEEP,
                        level * DURATION_SECONDS_PER_LEVEL * 20,
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