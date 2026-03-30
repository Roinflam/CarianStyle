package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
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
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 黑焰仪式附魔
 * 修复: getUsedItemHand -> InteractionHand.MAIN_HAND
 * 优化: LivingTickEvent -> PlayerTickEvent
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "black_flame_ritual", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.ARMOR_CHEST, slots = {EquipmentSlot.CHEST}, conflictsWith = {EnchantmentShelterOfFire.class, EnchantmentHealingByFire.class})
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameRitual extends EnchantmentBase {
    public EnchantmentBlackFlameRitual() { super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST}); }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getEntity() instanceof LivingEntity attacker)) return;
        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;
        // 修复：攻击者检查主手+护甲
        int totalLevel = EnchantmentHelper.getItemEnchantmentLevel(blackFlameRitual, attacker.getItemInHand(InteractionHand.MAIN_HAND));
        for (ItemStack armor : attacker.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blackFlameRitual, armor);
        }
        if (totalLevel <= 0) return;
        float damageMultiplier = 1;
        for (MobEffectInstance effect : attacker.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) {
                damageMultiplier += (!potion.isBeneficial()) ? 0.2f : 0.1f;
            }
        }
        evt.setAmount(evt.getAmount() * damageMultiplier);
    }

    /** 优化：从LivingTickEvent改为PlayerTickEvent，避免所有实体每tick进入 */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) return;
        if (evt.player.tickCount % 20 != 0) return;
        Player holder = evt.player;
        Enchantment blackFlameRitual = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameRitual.class);
        if (blackFlameRitual == null) return;
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blackFlameRitual, armor);
        }
        if (totalLevel <= 0) return;
        boolean hasPotion = false;
        for (MobEffectInstance effect : holder.getActiveEffects()) {
            MobEffect potion = effect.getEffect();
            if (!potion.isInstantenous() && effect.isVisible()) { hasPotion = true; break; }
        }
        if (hasPotion) {
            DynamicAttributeManager.apply(holder, DynamicAttributes.DESTRUCTION_FIRE_BURNING.createInstance(21, 0));
            holder.setHealth(holder.getHealth() * 0.95f);
        }
    }

    @Override protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
    @Override public int getMinCost(int l) { return (int)(30 * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
