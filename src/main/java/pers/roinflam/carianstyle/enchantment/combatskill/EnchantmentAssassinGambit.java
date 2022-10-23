package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;

@Mod.EventBusSubscriber
public class EnchantmentAssassinGambit extends RaryBase {

    public EnchantmentAssassinGambit(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "assassin_gambit");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ASSASSIN_GAMBIT;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getActivePotionEffect(CarianStylePotion.STEALTH) != null) {
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                        if (bonusLevel > 0) {
                            attacker.removePotionEffect(CarianStylePotion.STEALTH);
                            evt.setAmount(evt.getAmount() + evt.getAmount() * bonusLevel * 0.25f);
                        }
                    }
                }
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    if (bonusLevel > 0) {
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.STEALTH, bonusLevel * 20));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.isVanillaCritical()) {
                if (evt.getTarget() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    EntityPlayer attacker = evt.getEntityPlayer();
                    if (attacker.getActivePotionEffect(CarianStylePotion.STEALTH) != null) {
                        if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                            int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                            if (bonusLevel > 0) {
                                attacker.removePotionEffect(CarianStylePotion.STEALTH);
                                evt.setDamageModifier(evt.getDamageModifier() * 2);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

}
