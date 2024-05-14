package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentDragoncrestGreatshield extends VeryRaryBase {

    public EnchantmentDragoncrestGreatshield(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "dragoncrest_greatshield");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DRAGONCREST_GREATSHIELD;
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

                if (bonusLevel > 0) {
                    @Nullable PotionEffect potionEffect = hurter.getActivePotionEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD);
                    if (potionEffect == null) {
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD, 600, 0));
                    } else if (potionEffect.getAmplifier() < 19) {
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD, 600, potionEffect.getAmplifier() + 1));
                    } else {
                        hurter.addPotionEffect(new PotionEffect(CarianStylePotion.DRAGONCREST_GREATSHIELD, 600, 19));
                        evt.setAmount(evt.getAmount() * 0.75f);
                    }
                }
            }
        }
    }


    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(Enchantments.PROTECTION);
    }

}
