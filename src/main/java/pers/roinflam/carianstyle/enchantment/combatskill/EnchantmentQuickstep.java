package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 快步附魔
 *
 * 血量越低速度越快
 * 速度等级 = (损失血量百分比) / 5 × 附魔总等级
 */
@AutoRegisterEnchantment(
        id = "quickstep",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
@Mod.EventBusSubscriber
public class EnchantmentQuickstep extends EnchantmentBase {

    public EnchantmentQuickstep() {
        super(EnumEnchantmentType.ARMOR_FEET, new EntityEquipmentSlot[]{EntityEquipmentSlot.FEET});
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote || evt.phase != TickEvent.Phase.START) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        Enchantment quickstep = EnchantmentRegistry.getEnchantmentByClass(EnchantmentQuickstep.class);
        if (quickstep == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : player.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(quickstep, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - player.getHealth() / player.getMaxHealth();
        int speedLevel = (int) (missingHealthPercent * 100 / 5 * totalLevel);

        if (speedLevel > 0) {
            player.addPotionEffect(new PotionEffect(CarianStylePotion.SPEED_BOOST, 2, speedLevel - 1));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }
}