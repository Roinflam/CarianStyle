package pers.roinflam.carianstyle.enchantment.recollect;

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
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentDarkAbandonedChild extends VeryRaryBase {

    public EnchantmentDarkAbandonedChild(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "dark_abandoned_child");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DARK_ABANDONED_CHILD;
    }


    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
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
                        evt.getSource().setMagicDamage().setDamageBypassesArmor();
                        if (hurter.getActivePotionEffects().size() > 0) {
                            @Nonnull List<PotionEffect> potionEffects = new ArrayList<>(hurter.getActivePotionEffects());
                            potionEffects.removeIf(potionEffect ->
                                    potionEffect.getPotion().isBadEffect() ||
                                            potionEffect.getPotion().isInstant() ||
                                            !potionEffect.getPotion().shouldRender(potionEffect)
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
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                    
                if (bonusLevel > 0) {
                        if (!hurter.world.isDaytime()) {
                            evt.setAmount(evt.getAmount() * 0.9f);
                        }
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

                if (bonusLevel > 0) {
                            entityPlayer.heal(entityPlayer.getMaxHealth() * 0.015f / 20);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench);
    }
}
