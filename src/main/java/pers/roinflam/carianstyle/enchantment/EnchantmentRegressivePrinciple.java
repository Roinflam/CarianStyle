package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

/**
 * 回归法则附魔
 *
 * 护甲附魔，清除周围实体的药水效果
 * 每tick有5%概率触发：
 * - 清除周围（等级×3格）内所有有药水效果的实体的所有药水
 */
@AutoRegisterEnchantment(
        id = "regressive_principle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentRegressivePrinciple extends EnchantmentBase {

    public EnchantmentRegressivePrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 每tick概率清除周围实体的药水效果
     * 由于需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        // 5%概率触发
        if (!RandomUtil.percentageChance(5)) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        Enchantment regressivePrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentRegressivePrinciple.class);
        if (regressivePrinciple == null) {
            return;
        }

        // 从护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(regressivePrinciple, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 清除周围有药水效果的实体的所有药水
        EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                player,
                totalLevel * 3,
                entity -> !entity.getActivePotionEffects().isEmpty()
        ).forEach(EntityLivingBase::clearActivePotions);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}