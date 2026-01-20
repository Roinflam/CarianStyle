package pers.roinflam.carianstyle.enchantment.combatskill;

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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 刺客赌局附魔
 * <p>
 * 受击时获得隐身效果（等级×20tick）
 * 隐身状态下攻击：移除隐身，增伤+25%×等级
 * 隐身状态下暴击：移除隐身，暴击倍率×2
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

    /**
     * 攻击者视角：隐身状态下增伤
     * 受击者视角：获得隐身
     * 由于需要同时处理攻击者和受击者双方，保留静态监听器
     */
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
        if (DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
            ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    // 移除隐身效果
                    DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
                    // 增加伤害
                    evt.setAmount(evt.getAmount() + evt.getAmount() * level * 0.25f);
                }
            }
        }

        // 受击者视角：获得隐身
        ItemStack victimHeldItem = victim.getItemInHand(victim.getUsedItemHand());
        if (!victimHeldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, victimHeldItem);

            if (level > 0) {
                // 应用隐身效果
                DynamicAttributeManager.apply(victim,
                        DynamicAttributes.STEALTH.createInstance(level * 20));
            }
        }
    }

    /**
     * 暴击时：隐身状态下暴击倍率×2
     */
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

        // 检查是否拥有隐身效果
        if (!DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);

        if (level > 0) {
            // 移除隐身效果
            DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
            // 暴击倍率×2
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