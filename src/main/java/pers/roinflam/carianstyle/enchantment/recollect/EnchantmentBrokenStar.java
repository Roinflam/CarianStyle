package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 碎星附魔
 * <p>
 * 主手持有时：自身生命不低于 50% 时伤害 ×1.5（夜晚 ×2）；生命低于 50% 时受到的伤害 ×0.75（夜晚 ×0.5）。
 * </p>
 * <p>
 * 以前受击一侧的昼夜系数写反了（夜晚 ×0.75、白天 ×0.5，和「夜晚效果翻倍」正好相反），
 * 而且受击侧用的是「不高于 50%」，正好半血时攻守两档同时生效；现在受击侧是「低于 50%」。
 * 武器以前按「最近一次使用物品的那只手」取，用副手吃东西、举盾后会一直指向副手，现在一律看主手。
 * 怪物持有时按怪物触发附魔的开关处理。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "broken_star",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentBrokenStar extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 攻守两档的生命分界（占最大生命） */
    private static final float HEALTH_THRESHOLD = 0.5F;
    /** 白天攻击倍率 */
    private static final float ATTACK_MULTIPLIER_DAY = 1.5F;
    /** 夜晚攻击倍率 */
    private static final float ATTACK_MULTIPLIER_NIGHT = 2.0F;
    /** 白天受伤倍率 */
    private static final float DEFENSE_MULTIPLIER_DAY = 0.75F;
    /** 夜晚受伤倍率 */
    private static final float DEFENSE_MULTIPLIER_NIGHT = 0.5F;
    /** 等级上限 */
    private static final int LEVEL_CAP = 10;

    public EnchantmentBrokenStar() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        Enchantment brokenStar = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBrokenStar.class);
        if (brokenStar == null) {
            return;
        }

        // 攻击者视角
        if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();

            if (!shouldBlockMobTrigger(attacker, false)) {
                ItemStack heldItem = attacker.getHeldItemMainhand();
                if (!heldItem.isEmpty()) {
                    int level = capLevel(EnchantmentHelper.getEnchantmentLevel(brokenStar, heldItem), LEVEL_CAP);
                    if (level > 0 && attacker.getHealth() >= attacker.getMaxHealth() * HEALTH_THRESHOLD) {
                        evt.setAmount(evt.getAmount()
                                * (!attacker.world.isDaytime() ? ATTACK_MULTIPLIER_NIGHT : ATTACK_MULTIPLIER_DAY));
                    }
                }
            }
        }

        // 受击者视角
        if (!evt.getSource().canHarmInCreative()) {
            EntityLivingBase victim = evt.getEntityLiving();

            if (shouldBlockMobTrigger(victim, false)) {
                return;
            }

            ItemStack heldItem = victim.getHeldItemMainhand();
            if (!heldItem.isEmpty()) {
                int level = capLevel(EnchantmentHelper.getEnchantmentLevel(brokenStar, heldItem), LEVEL_CAP);
                if (level > 0 && victim.getHealth() < victim.getMaxHealth() * HEALTH_THRESHOLD) {
                    evt.setAmount(evt.getAmount()
                            * (!victim.world.isDaytime() ? DEFENSE_MULTIPLIER_NIGHT : DEFENSE_MULTIPLIER_DAY));
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}