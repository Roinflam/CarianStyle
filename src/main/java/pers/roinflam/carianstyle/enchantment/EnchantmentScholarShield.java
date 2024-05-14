package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentScholarShield extends VeryRaryBase {

    public EnchantmentScholarShield(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "scholar_shield");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.SCHOLAR_SHIELD;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (hurter.isHandActive()) {
                    @Nonnull ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                    if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            attacker.attackEntityFrom(DamageSource.causeMobDamage(hurter).setMagicDamage(), evt.getAmount() * bonusLevel * 0.1f);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (evt.getSource().getImmediateSource() != null) {
                if (hurter.isHandActive()) {
                    @Nonnull ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                    if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.075f);
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
}
