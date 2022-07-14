package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

@Mod.EventBusSubscriber
public class EnchantmentRegressivePrinciple extends RaryBase {

    public EnchantmentRegressivePrinciple(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "regressive_principle");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.REGRESSIVE_PRINCIPLE;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.END)) {
                if (RandomUtil.percentageChance(5)) {
                    EntityPlayer entityPlayer = evt.player;
                    if (entityPlayer.isEntityAlive()) {
                        int bonusLevel = 0;
                        for (ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (bonusLevel > 0) {
                            entityPlayer.world.getEntitiesWithinAABB(
                                    EntityLivingBase.class,
                                    new AxisAlignedBB(entityPlayer.getPosition()).expand(bonusLevel * 3, bonusLevel * 3, bonusLevel * 3),
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
        return 15 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
