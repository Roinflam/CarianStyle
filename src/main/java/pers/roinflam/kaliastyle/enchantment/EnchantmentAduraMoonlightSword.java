package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;
import pers.roinflam.kaliastyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentAduraMoonlightSword extends Enchantment {

    public EnchantmentAduraMoonlightSword(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "adura_moonlight_sword");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.ADURA_MOONLIGHT_SWORD;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        evt.getSource().setMagicDamage();
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                hurter,
                                bonusLevel,
                                bonusLevel,
                                entity -> entity instanceof EntityLivingBase && !entity.equals(attacker)
                        );
                        if (attacker.world.isDaytime()) {
                            for (Entity entity : entities) {
                                EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                if (entityLivingBase.isPotionActive(KaliaStylePotion.FROSTBITE)) {
                                    int level = Math.min(entityLivingBase.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                                    entityLivingBase.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, level));
                                } else {
                                    entityLivingBase.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, 0));
                                }
                            }
                        } else {
                            for (Entity entity : entities) {
                                EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                if (entityLivingBase.isPotionActive(KaliaStylePotion.FROSTBITE)) {
                                    int level = Math.min(entityLivingBase.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() + 2, 9);
                                    entityLivingBase.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, level));
                                } else {
                                    entityLivingBase.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, 1));
                                }
                            }
                        }
                    }
                }
            }
        }
    }


    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(KaliaStyleEnchantments.EPILEPSY_FIRE) &&
                !ench.equals(KaliaStyleEnchantments.EAT_SHIT) &&
                !ench.equals(KaliaStyleEnchantments.HYPNOTIC_SMOKE) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_DEVOURED);
    }

}
