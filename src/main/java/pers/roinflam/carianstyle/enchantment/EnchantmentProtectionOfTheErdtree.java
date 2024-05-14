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
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentProtectionOfTheErdtree extends RaryBase {

    public EnchantmentProtectionOfTheErdtree(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "protection_of_the_erdtree");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.PROTECTION_OF_THE_ERDTREE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
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
                    hurter.addPotionEffect(new PotionEffect(CarianStylePotion.PROTECTION_OF_THE_ERDTREE, (int) (2.5 * bonusLevel * 20), bonusLevel - 1));
                }
                bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : attacker.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    bonusLevel = Math.min(bonusLevel, 5);
                    attacker.addPotionEffect(new PotionEffect(CarianStylePotion.PROTECTION_OF_THE_ERDTREE, (int) (2.5 * bonusLevel * 20), bonusLevel - 1));
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
