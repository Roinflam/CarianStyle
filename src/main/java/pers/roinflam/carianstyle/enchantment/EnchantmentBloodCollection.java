package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

@Mod.EventBusSubscriber
public class EnchantmentBloodCollection extends RaryBase {

    public EnchantmentBloodCollection(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "blood_collection");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLOOD_COLLECTION;
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * (1 - attacker.getHealth() / attacker.getMaxHealth()) * bonusLevel * 0.15f);
                        if (EnchantmentHelper.getEnchantmentLevel(EnchantmentBloodSlash.getEnchantment(), attacker.getHeldItem(attacker.getActiveHand())) > 0) {
                            attacker.heal(attacker.getMaxHealth() * (1 - attacker.getHealth() / attacker.getMaxHealth()) * bonusLevel * 0.04f);
                        } else {
                            attacker.heal(attacker.getMaxHealth() * (1 - attacker.getHealth() / attacker.getMaxHealth()) * bonusLevel * 0.02f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER) &&
                !ench.equals(CarianStyleEnchantments.DARK_MOON);
    }
}
