package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 不动之盾附魔
 * <p>
 * 盾牌附魔，强化格挡效果
 * 格挡时：
 * - 若完全格挡伤害（amount <= 0）：清除攻击者所有药水效果，治疗自己（最大生命值 × 等级 × 1%）
 * - 否则：减伤 10% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "immutable_shield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
public class EnchantmentImmutableShield extends EnchantmentBase {

    public EnchantmentImmutableShield() {
        super(EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        // 必须正在使用物品
        if (!victim.isUsingItem()) {
            return;
        }

        // 必须是盾牌
        ItemStack activeItem = victim.getItemInHand(victim.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        // 检查附魔物品是否是当前使用的盾牌
        if (!ctx.getEnchantedItem().equals(activeItem)) {
            return;
        }

        if (ctx.getDamage() <= 0 && ctx.getAttacker() != null) {
            // 完全格挡：清除攻击者药水效果，治疗自己
            ctx.getAttacker().removeAllEffects();
            victim.heal(victim.getMaxHealth() * level * 0.01f);
        } else {
            // 未完全格挡：减伤 10% × 等级
            float reduction = ctx.getDamage() * level * 0.1f;
            ctx.reduceDamage(reduction);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class));
    }
}