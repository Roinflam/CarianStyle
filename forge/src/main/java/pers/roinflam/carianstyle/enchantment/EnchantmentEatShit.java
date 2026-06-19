package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 吃屎附魔
 * <p>v2.2：攻击者+治疗事件入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "eat_shit", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentEatShit extends EnchantmentBase {
    private static final String DEBUFF_KEY = "eat_shit_debuff";
    public EnchantmentEatShit() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        LivingEntity victim = evt.getEntity();
        Enchantment eatShit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class);
        if (eatShit == null) return;
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(eatShit, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        int victimDuration = level * 80;
        victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, victimDuration));
        attacker.addEffect(new MobEffectInstance(MobEffects.CONFUSION, level * 30));
        EnchantmentDataManager.setCooldown(DEBUFF_KEY, victim.getUUID(), victimDuration);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) return;

        // ⭐ v2.2：怪物附魔触发开关（受治疗者视角，被附加的debuff效果）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(evt.getEntity(), false)) return;

        if (EnchantmentDataManager.isOnCooldown(DEBUFF_KEY, evt.getEntity().getUUID())) {
            evt.setAmount(evt.getAmount() * 0.25f);
        }
    }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 2) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
