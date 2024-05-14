package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends VeryRaryBase {

    public EnchantmentDarkMoon(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "dark_moon");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DARK_MOON;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityLiving) {
                EntityLiving hurter = (EntityLiving) evt.getEntityLiving();
                if (evt.getSource().isMagicDamage()) {
                    if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            boolean hasFullMoon = false;
                            for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                                if (!itemStack.isEmpty()) {
                                    if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                        hasFullMoon = true;
                                        break;
                                    }
                                }
                            }
                            if (hasFullMoon) {
                                evt.setAmount(evt.getAmount() - evt.getAmount() * 0.375f);
                            } else {
                                evt.setAmount(evt.getAmount() - evt.getAmount() * 0.25f);
                            }
                        }
                    }
                    if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
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
                                boolean hasFullMoon = false;
                                for (@Nonnull ItemStack itemStack : attacker.getArmorInventoryList()) {
                                    if (!itemStack.isEmpty()) {
                                        if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                            hasFullMoon = true;
                                            break;
                                        }
                                    }
                                }
                                if (hasFullMoon) {
                                    evt.setAmount(evt.getAmount() + evt.getAmount() * 0.375f);
                                    if (hurter.getAttackTarget() != null && hurter.getAttackTarget().equals(attacker)) {
                                        evt.setAmount(evt.getAmount() + (hurter.getHealth() * 0.075f));
                                        attacker.heal(Math.min(evt.getAmount() * 0.075f, attacker.getMaxHealth() * 0.075f));
                                    }
                                } else {
                                    evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f);
                                    if (hurter.getAttackTarget() != null && hurter.getAttackTarget().equals(attacker)) {
                                        evt.setAmount(evt.getAmount() + (hurter.getHealth() * 0.05f));
                                        attacker.heal(Math.min(evt.getAmount() * 0.05f, attacker.getMaxHealth() * 0.05f));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (!healer.getHeldItem(healer.getActiveHand()).isEmpty()) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), healer.getHeldItem(healer.getActiveHand()));
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    boolean hasFullMoon = false;
                    for (@Nonnull ItemStack itemStack : healer.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                hasFullMoon = true;
                                break;
                            }
                        }
                    }
                    if (hasFullMoon) {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.375f);
                    } else {
                        evt.setAmount(evt.getAmount() + evt.getAmount() * 0.25f);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && !evt.player.world.isDaytime()) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                @Nonnull EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isEntityAlive()) {
                    if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItem(entityPlayer.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            entityPlayer.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 210));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(CarianStyleEnchantments.SCARLET_ROT) &&
                !ench.equals(CarianStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(CarianStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(CarianStyleEnchantments.VIC_DRAGON_THUNDER);
    }
}
