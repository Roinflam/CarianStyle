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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentLongTailCat extends RaryBase {

    public EnchantmentLongTailCat(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "long_tail_cat");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LONG_TAIL_CAT;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().damageType.equals("fall")) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    if (evt.getAmount() < hurter.getMaxHealth() * 0.5 + hurter.getMaxHealth() * (bonusLevel - 1) * 0.25) {
                        evt.setCanceled(true);
                    }
                }
            }
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
