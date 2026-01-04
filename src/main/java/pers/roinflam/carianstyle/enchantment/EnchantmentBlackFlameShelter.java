package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
 * 黑焰庇护附魔
 *
 * 受击：非魔法非无视防御伤害减少（等级×12.5%）
 * 代价：治疗量减少（等级×25%）
 */
@AutoRegisterEnchantment(
        id = "black_flame_shelter",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        conflictsWith = {
                EnchantmentShelterOfFire.class,
                EnchantmentHealingByFire.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentBlackFlameShelter extends EnchantmentBase {

    public EnchantmentBlackFlameShelter() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment blackFlameShelter = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlackFlameShelter.class);
        if (blackFlameShelter == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(blackFlameShelter, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        if (damageSource.isMagicDamage() || damageSource.isUnblockable()) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (1 - totalLevel * 0.125f));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase holder = evt.getEntityLiving();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() * (1 - totalLevel * 0.25f));
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        if (ench == Enchantments.PROTECTION) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}