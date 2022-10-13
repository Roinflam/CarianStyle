package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;

@Mod.EventBusSubscriber
public class EnchantmentMillicentProsthesis extends RaryBase {

    public EnchantmentMillicentProsthesis(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "millicent_prosthesis");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.MILLICENT_PROSTHESIS;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        PotionEffect potionEffect = attacker.getActivePotionEffect(CarianStylePotion.ATTACK_BOOST);
                        if (potionEffect == null) {
                            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.ATTACK_BOOST, 30, bonusLevel - 1));
                        } else if (potionEffect.getAmplifier() < bonusLevel * 7 - 1) {
                            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.ATTACK_BOOST, 30, potionEffect.getAmplifier() + bonusLevel));
                        } else {
                            attacker.addPotionEffect(new PotionEffect(CarianStylePotion.ATTACK_BOOST, 60, bonusLevel * 16 - 1));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        PotionEffect potionEffect = attacker.getActivePotionEffect(CarianStylePotion.ATTACK_BOOST);
                        if (potionEffect != null && potionEffect.getAmplifier() >= bonusLevel * 7 - 1) {
                            evt.setAmount(evt.getAmount() + evt.getAmount() * bonusLevel * 0.1f);
                        } else {
                            evt.setAmount(evt.getAmount() + evt.getAmount() * bonusLevel * 0.05f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 25;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER) &&
                !ench.equals(CarianStyleEnchantments.DARK_MOON) &&
                !ench.equals(CarianStyleEnchantments.RED_FEATHERED_BRANCHSWORD) &&
                !ench.equals(CarianStyleEnchantments.BLUE_FEATHERED_BRANCHSWORD);
    }

}
