package pers.roinflam.kaliastyle.enchantment;

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
import pers.roinflam.kaliastyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends Enchantment {

    public EnchantmentDarkMoon(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "dark_moon");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.DARK_MOON;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityLiving) {
                EntityLiving hurter = (EntityLiving) evt.getEntityLiving();
                if (evt.getSource().isMagicDamage()) {
                    if (hurter.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            boolean hasFullMoon = false;
                            for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                                if (itemStack != null) {
                                    if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                        hasFullMoon = true;
                                        break;
                                    }
                                }
                            }
                            if (hasFullMoon) {
                                evt.setAmount((float) (evt.getAmount() - evt.getAmount() * 0.375));
                            } else {
                                evt.setAmount((float) (evt.getAmount() - evt.getAmount() * 0.25));
                            }
                        }
                    }
                    if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                        EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                        if (attacker.getHeldItemMainhand() != null) {
                            int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                            if (bonusLevel > 0) {
                                boolean hasFullMoon = false;
                                for (ItemStack itemStack : attacker.getArmorInventoryList()) {
                                    if (itemStack != null) {
                                        if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                            hasFullMoon = true;
                                            break;
                                        }
                                    }
                                }
                                if (hasFullMoon) {
                                    evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.375));
                                    if (hurter.getAttackTarget() != null && hurter.getAttackTarget().equals(attacker)) {
                                        evt.setAmount(evt.getAmount() + (float) (hurter.getHealth() * 0.075));
                                        attacker.heal((float) Math.min(evt.getAmount() * 0.075, attacker.getMaxHealth() * 0.075));
                                    }
                                } else {
                                    evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.25));
                                    if (hurter.getAttackTarget() != null && hurter.getAttackTarget().equals(attacker)) {
                                        evt.setAmount(evt.getAmount() + (float) (hurter.getHealth() * 0.05));
                                        attacker.heal((float) Math.min(evt.getAmount() * 0.05, attacker.getMaxHealth() * 0.05));
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
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.getHeldItemMainhand() != null) {
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), healer.getHeldItemMainhand());
                if (bonusLevel > 0) {
                    boolean hasFullMoon = false;
                    for (ItemStack itemStack : healer.getArmorInventoryList()) {
                        if (itemStack != null) {
                            if (EnchantmentHelper.getEnchantmentLevel(EnchantmentFullMoon.getEnchantment(), itemStack) > 0) {
                                hasFullMoon = true;
                                break;
                            }
                        }
                    }
                    if (hasFullMoon) {
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.375));
                    } else {
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.25));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && !evt.player.world.isDaytime()) {
            if (evt.phase.equals(TickEvent.Phase.END)) {
                EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isEntityAlive()) {
                    if (entityPlayer.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            entityPlayer.addPotionEffect(new PotionEffect(MobEffects.NIGHT_VISION, 210));
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
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 35;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(KaliaStyleEnchantments.SCARLET_CORRUPTION) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_DEVOURED) &&
                !ench.equals(KaliaStyleEnchantments.VIC_DRAGON_THUNDER);
    }
}
