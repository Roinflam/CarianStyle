package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentGoldenDungTurtle extends UncommonBase {

    public EnchantmentGoldenDungTurtle(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "golden_dung_turtle");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GOLDEN_DUNG_TURTLE;
    }

    @SubscribeEvent
    public static void onLivingExperienceDrop(@Nonnull LivingExperienceDropEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getAttackingPlayer() != null) {
                EntityPlayer player = evt.getAttackingPlayer();
                if (!player.getHeldItem(player.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), player.getHeldItem(player.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        evt.setDroppedExperience((int) (evt.getDroppedExperience() + evt.getDroppedExperience() * bonusLevel * 0.3));
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

}
