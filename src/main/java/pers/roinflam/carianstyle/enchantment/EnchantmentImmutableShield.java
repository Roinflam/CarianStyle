package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentImmutableShield extends RaryBase {

    public EnchantmentImmutableShield(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "immutable_shield");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.IMMUTABLE_SHIELD;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (hurter.isHandActive()) {
                @Nonnull ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (evt.getAmount() <= 0 && evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                            @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                            attacker.clearActivePotions();
                            hurter.heal(hurter.getMaxHealth() * bonusLevel * 0.01f);
                        } else {
                            evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.1f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.SCHOLAR_SHIELD);
    }
}
