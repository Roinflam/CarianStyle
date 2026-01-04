package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
 * 戴狄卡之祸附魔（诅咒）
 *
 * 护甲诅咒附魔
 * 受到伤害时：
 * - 伤害 ×5（严重增伤）
 * - 无敌时间减少到75%（更容易连续受击）
 */
@AutoRegisterEnchantment(
        id = "daedicar_woe",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentDaedicarWoe extends EnchantmentBase {

    public EnchantmentDaedicarWoe() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment daedicarWoe = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDaedicarWoe.class);
        if (daedicarWoe == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(daedicarWoe, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime * 0.75);
        evt.setAmount(evt.getAmount() * 5);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isCurse() {
        return true;
    }
}