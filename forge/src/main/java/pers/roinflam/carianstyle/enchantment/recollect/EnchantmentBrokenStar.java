package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
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
 *
 * 攻击者（血量>=50%）：夜晚伤害×2，白天×1.5
 * 受击者（血量<=50%）：夜晚减伤25%，白天减伤50%
 */
@AutoRegisterEnchantment(
        id = "broken_star",
        category = EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentBrokenStar extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

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

            if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        brokenStar,
                        attacker.getHeldItem(attacker.getActiveHand()));

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    if (attacker.getHealth() >= attacker.getMaxHealth() / 2) {
                        if (!attacker.world.isDaytime()) {
                            evt.setAmount(evt.getAmount() * 2);
                        } else {
                            evt.setAmount(evt.getAmount() * 1.5f);
                        }
                    }
                }
            }
        }

        // 受击者视角
        if (!evt.getSource().canHarmInCreative()) {
            EntityLivingBase victim = evt.getEntityLiving();

            if (!victim.getHeldItem(victim.getActiveHand()).isEmpty()) {
                int level = EnchantmentHelper.getEnchantmentLevel(
                        brokenStar,
                        victim.getHeldItem(victim.getActiveHand()));

                if (level > 0) {
                    if (victim.getHealth() <= victim.getMaxHealth() / 2) {
                        if (!victim.world.isDaytime()) {
                            evt.setAmount(evt.getAmount() * 0.75f);
                        } else {
                            evt.setAmount(evt.getAmount() * 0.5f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }
}