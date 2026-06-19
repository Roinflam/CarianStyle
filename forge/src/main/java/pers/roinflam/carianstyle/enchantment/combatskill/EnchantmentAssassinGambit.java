package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 刺客赌局附魔
 * <p>v2.2：双向监听器+暴击事件入口接入怪物附魔触发开关。
 * CriticalHitEvent 仅玩家触发，开关检查作为安全网保留。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentAssassinGambit extends EnchantmentBase {

    public EnchantmentAssassinGambit() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        // 攻击者视角：隐身状态下增伤
        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
            if (DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);
                    if (ConfigLoader.levelLimit) {
                        level = Math.min(level, 10);
                    }
                    if (level > 0) {
                        DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
                        evt.setAmount(evt.getAmount() + evt.getAmount() * level * 0.25f);
                    }
                }
            }
        }

        // 受击者视角：获得隐身
        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack victimHeldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!victimHeldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, victimHeldItem);
            if (level > 0) {
                DynamicAttributeManager.apply(victim,
                        DynamicAttributes.STEALTH.createInstance(level * 20));
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@NotNull CriticalHitEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        if (!evt.isVanillaCritical()) {
            return;
        }
        if (!(evt.getTarget() instanceof LivingEntity)) {
            return;
        }

        Player attacker = evt.getEntity();

        // ⭐ v2.2：CriticalHitEvent 仅玩家触发，开关检查作为安全网（玩家始终放行）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        if (!DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);
        if (level > 0) {
            DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
            evt.setDamageModifier(evt.getDamageModifier() * 2);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
