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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentBlackFlameShelter extends RaryBase {

    public EnchantmentBlackFlameShelter(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "black_flame_shelter");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLACK_FLAME_SHELTER;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.isMagicDamage() && !damageSource.isUnblockable()) {
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
                    evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.125f);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            if (ConfigLoader.levelLimit) {
                bonusLevel = Math.min(bonusLevel, 10);
            }
            if (bonusLevel > 0) {
                evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.25f);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(Enchantments.PROTECTION) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE);
    }

}
