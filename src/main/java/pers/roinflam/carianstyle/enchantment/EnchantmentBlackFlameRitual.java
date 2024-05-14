package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
public class EnchantmentBlackFlameRitual extends VeryRaryBase {

    public EnchantmentBlackFlameRitual(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "black_flame_ritual");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLACK_FLAME_RITUAL;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                for (@Nonnull ItemStack itemStack : attacker.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    float damageMultiplier = 1;
                    for (PotionEffect potionEffect : attacker.getActivePotionEffects()) {
                        Potion potion = potionEffect.getPotion();
                        if (!potion.isInstant() && potionEffect.getPotion().shouldRender(potionEffect)) {
                            damageMultiplier += potion.isBadEffect() ? 0.2 : 0.1;
                        }
                    }
                    evt.setAmount(evt.getAmount() * damageMultiplier);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (!evt.getEntity().world.isRemote && evt.getEntity().world.getTotalWorldTime() % 20 == 0) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            int bonusLevel = 0;
            for (@Nonnull ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            
                if (bonusLevel > 0) {
                boolean hasPotion = false;
                for (PotionEffect potionEffect : entityLivingBase.getActivePotionEffects()) {
                    Potion potion = potionEffect.getPotion();
                    if (!potion.isInstant() && potionEffect.getPotion().shouldRender(potionEffect)) {
                        hasPotion = true;
                        break;
                    }
                }
                if (hasPotion) {
                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.DESTRUCTION_FIRE_BURNING, 21, 0));
                    entityLivingBase.setHealth(entityLivingBase.getHealth() * 0.95f);
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
                !ench.equals(Enchantments.PROTECTION) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE);
    }

}
