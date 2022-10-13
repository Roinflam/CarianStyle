package pers.roinflam.carianstyle.enchantment;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentAduraMoonlightSword extends RaryBase {

    public EnchantmentAduraMoonlightSword(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "adura_moonlight_sword");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ADURA_MOONLIGHT_SWORD;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        evt.getSource().setMagicDamage();
                        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                bonusLevel,
                                entityLivingBase -> !entityLivingBase.equals(attacker)
                        );
                        if (attacker.world.isDaytime()) {
                            for (EntityLivingBase entityLivingBase : entities) {
                                if (entityLivingBase.isPotionActive(CarianStylePotion.FROSTBITE)) {
                                    int level = Math.min(entityLivingBase.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, level));
                                } else {
                                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, 0));
                                }
                            }
                        } else {
                            for (Entity entity : entities) {
                                EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                if (entityLivingBase.isPotionActive(CarianStylePotion.FROSTBITE)) {
                                    int level = Math.min(entityLivingBase.getActivePotionEffect(CarianStylePotion.FROSTBITE).getAmplifier() + 2, 9);
                                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, level));
                                } else {
                                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, 200, 1));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.EPILEPSY_FIRE) &&
                !ench.equals(CarianStyleEnchantments.EAT_SHIT) &&
                !ench.equals(CarianStyleEnchantments.HYPNOTIC_SMOKE) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED);
    }

}
