package pers.roinflam.kaliastyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.java.random.RandomUtil;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentDarkAbandonedChild extends Enchantment {

    public EnchantmentDarkAbandonedChild(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "dark_abandoned_child");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.DARK_ABANDONED_CHILD;
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        evt.getSource().setMagicDamage().setDamageBypassesArmor();
                        if (hurter.getActivePotionEffects().size() > 0) {
                            List<PotionEffect> potionEffects = new ArrayList<>(hurter.getActivePotionEffects());
                            potionEffects.removeIf(potionEffect ->
                                    potionEffect.getPotion().isBadEffect() ||
                                            potionEffect.getPotion().isInstant()
                            );
                            if (potionEffects.size() > 0) {
                                PotionEffect potionEffect = potionEffects.get(RandomUtil.getInt(0, potionEffects.size() - 1));
                                attacker.addPotionEffect(potionEffect);
                                hurter.removePotionEffect(potionEffect.getPotion());
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (!hurter.world.isDaytime()) {
                            evt.setAmount((float) (evt.getAmount() * 0.9));
                        }
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
                            entityPlayer.heal((float) (entityPlayer.getMaxHealth() * 0.015 / 20));
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
        return !KaliaStyleEnchantments.RECOLLECT.contains(ench);
    }
}
