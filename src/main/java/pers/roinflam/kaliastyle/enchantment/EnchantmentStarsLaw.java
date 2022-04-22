package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentStarsLaw extends Enchantment {

    public EnchantmentStarsLaw(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "stars_law");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.STARS_LAW;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!evt.getSource().isMagicDamage()) {
                    if (hurter.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            if (attacker.isPotionActive(KaliaStylePotion.FROSTBITE)) {
                                int level = Math.min(attacker.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() + 1, 5);
                                attacker.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, level));
                            } else {
                                attacker.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, 0));
                            }
                        }
                    }
                }
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (hurter.isPotionActive(KaliaStylePotion.FROSTBITE)) {
                            if (evt.getSource().isMagicDamage()) {
                                evt.setAmount((float) (evt.getAmount() + evt.getAmount() * hurter.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() * 0.05));
                            }
                            int level = Math.min(hurter.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                            hurter.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, level));
                        } else {
                            hurter.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, 0));
                        }
                    }
                }
            } else if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                if (hurter.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (!evt.getSource().isMagicDamage()) {
                            if (attacker.isPotionActive(KaliaStylePotion.FROSTBITE)) {
                                int level = Math.min(attacker.getActivePotionEffect(KaliaStylePotion.FROSTBITE).getAmplifier() + 1, 9);
                                attacker.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, level));
                            } else {
                                attacker.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, 200, 0));
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
                    evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.5));
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
                            entityPlayer.addPotionEffect(new PotionEffect(MobEffects.SPEED, 50));
                            entityPlayer.addPotionEffect(new PotionEffect(MobEffects.JUMP_BOOST, 50));
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
        return KaliaStyleEnchantments.RECOLLECT_ENCHANTABILITY;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                (ench.equals(KaliaStyleEnchantments.DARK_ABANDONED_CHILD) || !KaliaStyleEnchantments.RECOLLECT.contains(ench)) &&
                !ench.equals(KaliaStyleEnchantments.SCARLET_CORRUPTION) &&
                !ench.equals(KaliaStyleEnchantments.FIRE_GIVES_POWER) &&
                !ench.equals(KaliaStyleEnchantments.VIC_DRAGON_THUNDER);
    }

}
