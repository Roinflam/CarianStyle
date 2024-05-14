package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentAduraMoonlightSword extends RaryBase {

    public EnchantmentAduraMoonlightSword(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "adura_moonlight_sword");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ADURA_MOONLIGHT_SWORD;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (attacker instanceof EntityPlayer) {
                            if (EntityLivingUtil.getTicksSinceLastSwing((EntityPlayer) attacker) != 1) {
                                return;
                            }
                        }
                        evt.getSource().setMagicDamage();
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                bonusLevel,
                                entityLivingBase -> !entityLivingBase.equals(attacker)
                        );
                        if (attacker.world.isDaytime()) {
                            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
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
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
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
