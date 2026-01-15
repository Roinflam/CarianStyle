package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

/**
 * 不屈附魔
 *
 * 血量越低越有概率完全免疫伤害
 * 免疫概率 = 损失血量百分比 × 75%
 */
@AutoRegisterEnchantment(
        id = "indomitable",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentIndomitable extends EnchantmentBase {

    public EnchantmentIndomitable() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 使用静态事件因为需要累加所有护甲的附魔等级
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 排除无视创造模式的伤害
        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        Enchantment indomitable = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIndomitable.class);
        if (indomitable == null) {
            return;
        }

        // 累加所有护甲的附魔等级
        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(indomitable, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        // 免疫概率 = 损失血量百分比 × 75%
        float missingHealthPercent = 1 - holder.getHealth() / holder.getMaxHealth();
        double immuneChance = missingHealthPercent * 100 * 0.75;

        if (RandomUtil.percentageChance(immuneChance)) {
            evt.setCanceled(true);
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与原版保护类附魔冲突
        if (ench == Enchantments.PROTECTION ||
                ench == Enchantments.FIRE_PROTECTION ||
                ench == Enchantments.PROJECTILE_PROTECTION ||
                ench == Enchantments.BLAST_PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }
}