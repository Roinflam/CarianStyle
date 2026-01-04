package pers.roinflam.carianstyle.enchantment.combatskill;

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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

/**
 * 盾击附魔
 *
 * 举盾格挡时被攻击，将攻击者击退（击退强度 = 等级 × 0.25）
 */
@AutoRegisterEnchantment(
        id = "shield_bash",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentShieldBash extends EnchantmentBase {

    public EnchantmentShieldBash() {
        super(CarianStyleEnchantments.SHIELD, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND,
                EntityEquipmentSlot.OFFHAND
        });
    }

    @Override
    protected void onHurtAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
        // 攻击者必须是生物实体（排除箭矢等投射物）
        if (ctx.getDamageSource() == null ||
                !(ctx.getDamageSource().getImmediateSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase holder = ctx.getHolder();
        EntityLivingBase attacker = (EntityLivingBase) ctx.getDamageSource().getImmediateSource();

        // 检查是否正在举盾
        if (!holder.isHandActive()) {
            return;
        }

        ItemStack activeItem = holder.getHeldItem(holder.getActiveHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ItemShield)) {
            return;
        }

        // 确保当前附魔物品是正在使用的盾牌
        if (!ctx.getEnchantedItem().equals(activeItem)) {
            return;
        }

        // 击退攻击者
        double x = holder.posX - attacker.posX;
        double z = holder.posZ - attacker.posZ;
        attacker.knockBack(holder, level * 0.25f, x, z);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}