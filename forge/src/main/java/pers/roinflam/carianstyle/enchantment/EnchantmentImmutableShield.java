package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 不动之盾附魔
 *
 * 盾牌附魔，强化格挡效果
 * 格挡时：
 * - 若完全格挡伤害（amount <= 0）：清除攻击者所有药水效果，治疗自己（最大生命值 × 等级 × 1%）
 * - 否则：减伤 10% × 等级
 */
@AutoRegisterEnchantment(
        id = "immutable_shield",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentImmutableShield extends EnchantmentBase {

    public EnchantmentImmutableShield() {
        // 盾牌使用BREAKABLE类型，槽位为副手和主手
        super(EnumEnchantmentType.BREAKABLE, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    /**
     * 格挡时触发效果
     */
    @Override
    protected void onHurtAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        // 必须正在使用物品
        if (!victim.isHandActive()) {
            return;
        }

        // 必须是盾牌
        ItemStack activeItem = victim.getHeldItem(victim.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        // 检查附魔物品是否是当前使用的盾牌
        if (!ctx.getEnchantedItem().equals(activeItem)) {
            return;
        }

        if (ctx.getDamage() <= 0 && ctx.getAttacker() != null) {
            // 完全格挡：清除攻击者药水效果，治疗自己
            ctx.getAttacker().clearActivePotions();
            victim.heal(victim.getMaxHealth() * level * 0.01f);
        } else {
            // 未完全格挡：减伤 10% × 等级
            float reduction = ctx.getDamage() * level * 0.1f;
            ctx.reduceDamage(reduction);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class));
    }
}