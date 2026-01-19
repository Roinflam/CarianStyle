package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 附魔事件处理器
 * 专门处理EnchantmentBase的所有静态监听器
 * 与子类的@Mod.EventBusSubscriber分离,避免重复注册
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class EnchantmentEventHandler {

    // ==================== LivingAttackEvent 监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingAttackHighest(@Nonnull LivingAttackEvent event) {
        EnchantmentBase.handleLivingAttack(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingAttackHigh(@Nonnull LivingAttackEvent event) {
        EnchantmentBase.handleLivingAttack(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingAttackNormal(@Nonnull LivingAttackEvent event) {
        EnchantmentBase.handleLivingAttack(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingAttackLow(@Nonnull LivingAttackEvent event) {
        EnchantmentBase.handleLivingAttack(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingAttackLowest(@Nonnull LivingAttackEvent event) {
        EnchantmentBase.handleLivingAttack(event, EventPriority.LOWEST);
    }

    // ==================== LivingHurtEvent 监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingHurtHighest(@Nonnull LivingHurtEvent event) {
        EnchantmentBase.handleLivingHurt(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingHurtHigh(@Nonnull LivingHurtEvent event) {
        EnchantmentBase.handleLivingHurt(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHurtNormal(@Nonnull LivingHurtEvent event) {
        EnchantmentBase.handleLivingHurt(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingHurtLow(@Nonnull LivingHurtEvent event) {
        EnchantmentBase.handleLivingHurt(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingHurtLowest(@Nonnull LivingHurtEvent event) {
        EnchantmentBase.handleLivingHurt(event, EventPriority.LOWEST);
    }

    // ==================== LivingDamageEvent 监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDamageHighest(@Nonnull LivingDamageEvent event) {
        EnchantmentBase.handleLivingDamage(event, EventPriority.HIGHEST);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void handleLivingDamageHigh(@Nonnull LivingDamageEvent event) {
        EnchantmentBase.handleLivingDamage(event, EventPriority.HIGH);
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingDamageNormal(@Nonnull LivingDamageEvent event) {
        EnchantmentBase.handleLivingDamage(event, EventPriority.NORMAL);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void handleLivingDamageLow(@Nonnull LivingDamageEvent event) {
        EnchantmentBase.handleLivingDamage(event, EventPriority.LOW);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void handleLivingDamageLowest(@Nonnull LivingDamageEvent event) {
        EnchantmentBase.handleLivingDamage(event, EventPriority.LOWEST);
    }

    // ==================== 其他事件监听器 ====================

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void handleLivingDeath(@Nonnull LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = event.getEntity();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = victim.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, victim, stack, level,
                            null, victim, event.getSource()
                    );
                    baseEnchantment.onDeath(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleLivingHeal(@Nonnull LivingHealEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity healer = event.getEntity();

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = healer.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, healer, stack, level
                    );
                    baseEnchantment.onHeal(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent
    public static void handlePlayerTick(@Nonnull TickEvent.PlayerTickEvent event) {
        if (event.player.level().isClientSide) {
            return;
        }

        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = event.player;

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            for (Enchantment enchantment : EnchantmentHelper.getEnchantments(stack).keySet()) {
                if (!(enchantment instanceof EnchantmentBase)) {
                    continue;
                }

                EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
                int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
                level = baseEnchantment.applyLevelLimit(level);

                if (level > 0) {
                    EnchantmentContext ctx = new EnchantmentContext(
                            event, player, stack, level
                    );
                    baseEnchantment.onPlayerTick(ctx, level);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public static void handleCriticalHit(@Nonnull CriticalHitEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        Player player = event.getEntity();
        ItemStack weapon = player.getItemInHand(InteractionHand.MAIN_HAND);

        if (weapon.isEmpty()) {
            return;
        }

        for (Enchantment enchantment : EnchantmentHelper.getEnchantments(weapon).keySet()) {
            if (!(enchantment instanceof EnchantmentBase)) {
                continue;
            }

            EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;
            int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon);
            level = baseEnchantment.applyLevelLimit(level);

            if (level > 0) {
                EnchantmentContext ctx = new EnchantmentContext(
                        event, player, weapon, level
                );
                baseEnchantment.onCriticalHit(ctx, level);
            }
        }
    }
}