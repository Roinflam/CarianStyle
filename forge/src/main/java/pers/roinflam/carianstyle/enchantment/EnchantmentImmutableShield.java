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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

/**
 * 不动之盾附魔
 * <p>
 * 修复记录：构造函数 EnchantmentCategory.BREAKABLE → CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD")
 * 原bug：所有可损坏物品都能附上此盾牌专属附魔
 * </p>
 *
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "immutable_shield", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, customType = "SHIELD", slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND})
public class EnchantmentImmutableShield extends EnchantmentBase {

    public EnchantmentImmutableShield() {
        // 修复：BREAKABLE → SHIELD自定义类型
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();
        if (!victim.isUsingItem()) return;
        ItemStack activeItem = victim.getItemInHand(victim.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) return;
        if (!ctx.getEnchantedItem().equals(activeItem)) return;

        if (ctx.getDamage() <= 0 && ctx.getAttacker() != null) {
            // 完全格挡：清除攻击者药水效果，治疗自己
            ctx.getAttacker().removeAllEffects();
            victim.heal(victim.getMaxHealth() * level * 0.01f);
        } else {
            // 未完全格挡：减伤 10% × 等级
            ctx.reduceDamage(ctx.getDamage() * level * 0.1f);
        }
    }

    @Override public int getMinCost(int l) { return (int)((5 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class));
    }
}
