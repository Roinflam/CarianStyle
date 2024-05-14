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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentRegressivePrinciple extends RaryBase {

    public EnchantmentRegressivePrinciple(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "regressive_principle");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.REGRESSIVE_PRINCIPLE;
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                if (RandomUtil.percentageChance(5)) {
                    @Nonnull EntityPlayer entityPlayer = evt.player;
                    if (entityPlayer.isEntityAlive()) {
                        int bonusLevel = 0;
                        for (@Nonnull ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            EntityUtil.getNearbyEntities(
                                            EntityLivingBase.class,
                                            entityPlayer,
                                            bonusLevel * 3,
                                            entityLivingBase -> entityLivingBase.getActivePotionEffects().size() > 0)
                                    .forEach((entityLivingBase) -> {
                                        entityLivingBase.clearActivePotions();
                                    });
                        }
                    }
                }
            }
        }
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
