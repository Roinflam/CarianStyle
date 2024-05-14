package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentLucidity extends UncommonBase {

    public EnchantmentLucidity(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "lucidity");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LUCIDITY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent evt) {
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
                @Nonnull PotionEffect potionEffect = evt.getPotionEffect();
                @Nonnull Potion potion = potionEffect.getPotion();
                if (!potion.isInstant() && potionEffect.getPotion().shouldRender(potionEffect) && potion.isBadEffect()) {
                    evt.getPotionEffect().combine(new PotionEffect(potionEffect.getPotion(), (int) (potionEffect.getDuration() - potionEffect.getDuration() * bonusLevel * 0.15), potionEffect.getAmplifier() + 1));
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
