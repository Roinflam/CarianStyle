package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 黑焰仪式附魔
 *
 * 攻击：根据自身药水效果数量增伤（正面+10%，负面+20%）
 * 被动：有药水效果时每秒扣血5%并施加灭绝火焰
 */
@AutoRegisterEnchantment(
        id = "black_flame_ritual",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameRitual extends EnchantmentBase {

    public EnchantmentBlackFlameRitual() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = EnchantmentHelper.getEnchantmentLevel(
                blackFlameRitual,
                attacker.getHeldItem(attacker.getActiveHand()));

        for (ItemStack armor : attacker.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float damageMultiplier = 1;
        for (PotionEffect effect : attacker.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (!potion.isInstant() && potion.shouldRender(effect)) {
                damageMultiplier += potion.isBadEffect() ? 0.2f : 0.1f;
            }
        }

        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getEntity().world.getTotalWorldTime() % 20 != 0) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        boolean hasPotion = false;
        for (PotionEffect effect : holder.getActivePotionEffects()) {
            Potion potion = effect.getPotion();
            if (!potion.isInstant() && potion.shouldRender(effect)) {
                hasPotion = true;
                break;
            }
        }

        if (hasPotion) {
            holder.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, 21, 0));
            holder.setHealth(holder.getHealth() * 0.95f);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }
}