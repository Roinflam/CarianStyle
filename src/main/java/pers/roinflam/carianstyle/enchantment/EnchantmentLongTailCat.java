package pers.roinflam.carianstyle.enchantment;

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

import javax.annotation.Nonnull;

/**
 * 长尾猫附魔
 *
 * 护甲附魔，减免摔落伤害
 * 受到摔落伤害时：
 * - 如果伤害 < 最大生命值 × (50% + (等级-1) × 25%)，则完全取消伤害
 * - 等级1: 可免疫50%血量以下的摔落伤害
 * - 等级2: 可免疫75%血量以下的摔落伤害
 * - 等级3: 可免疫100%血量以下的摔落伤害
 */
@AutoRegisterEnchantment(
        id = "long_tail_cat",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentLongTailCat extends EnchantmentBase {

    public EnchantmentLongTailCat() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 摔落伤害低于阈值时完全取消
     * 由于需要累加所有护甲的附魔等级，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须是摔落伤害
        if (!evt.getSource().damageType.equals("fall")) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment longTailCat = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLongTailCat.class);
        if (longTailCat == null) {
            return;
        }

        // 从所有护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(longTailCat, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 计算伤害阈值：最大生命值 × (50% + (等级-1) × 25%)
        float threshold = victim.getMaxHealth() * 0.5f + victim.getMaxHealth() * (totalLevel - 1) * 0.25f;

        // 如果摔落伤害低于阈值，完全取消
        if (evt.getAmount() < threshold) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.FEATHER_FALLING);
    }
}