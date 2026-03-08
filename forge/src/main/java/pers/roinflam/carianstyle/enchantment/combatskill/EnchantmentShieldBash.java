package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 盾击附魔
 * <p>
 * 举盾格挡时被攻击，将攻击者击退（击退强度 = 等级 × 0.25）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "shield_bash",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
public class EnchantmentShieldBash extends EnchantmentBase {

    public EnchantmentShieldBash() {
        super(net.minecraft.world.item.enchantment.EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        // 攻击者必须是生物实体（排除箭矢等投射物）
        if (ctx.getDamageSource() == null ||
                !(ctx.getDamageSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = ctx.getHolder();
        LivingEntity attacker = (LivingEntity) ctx.getDamageSource().getDirectEntity();

        // 检查是否正在举盾
        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        // 确保当前附魔物品是正在使用的盾牌
        if (!ctx.getEnchantedItem().equals(activeItem)) {
            return;
        }

        // 击退攻击者
        double x = holder.getX() - attacker.getX();
        double z = holder.getZ() - attacker.getZ();
        attacker.knockback(level * 0.25f, x, z);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}