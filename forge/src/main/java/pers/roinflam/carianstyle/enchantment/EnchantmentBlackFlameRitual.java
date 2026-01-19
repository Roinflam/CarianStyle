package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 黑焰仪式附魔
 * <p>
 * 攻击：根据自身药水效果数量增伤（正面+10%，负面+20%）
 * 被动：有药水效果时每秒扣血5%并施加灭绝火焰
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "black_flame_ritual",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameRitual extends EnchantmentBase {

    public EnchantmentBlackFlameRitual() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = EnchantmentHelper.getItemEnchantmentLevel(
                blackFlameRitual,
                attacker.getItemInHand(attacker.getUsedItemHand()));

        for (ItemStack armor : attacker.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float damageMultiplier = 1;
        for (MobEffectInstance effect : attacker.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) {
                // 1.20.1: isBadEffect → !isBeneficial (逻辑反转)
                damageMultiplier += (!potion.isBeneficial()) ? 0.2f : 0.1f;
            }
        }

        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    @SubscribeEvent
    public static void onLivingTick(@NotNull LivingEvent.LivingTickEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getEntity().tickCount % 20 != 0) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blackFlameRitual, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        boolean hasPotion = false;
        for (MobEffectInstance effect : holder.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) {
                hasPotion = true;
                break;
            }
        }

        if (hasPotion) {
            holder.addEffect(new MobEffectInstance(CarianStylePotion.DESTRUCTION_FIRE_BURNING.get(), 21, 0));
            holder.setHealth(holder.getHealth() * 0.95f);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.ALL_DAMAGE_PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}