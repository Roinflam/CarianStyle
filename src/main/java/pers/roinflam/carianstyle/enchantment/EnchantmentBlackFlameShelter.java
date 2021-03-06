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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentBlackFlameShelter extends RaryBase {

    public EnchantmentBlackFlameShelter(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "black_flame_shelter");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BLACK_FLAME_SHELTER;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.isMagicDamage() && !damageSource.isUnblockable()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    evt.setAmount((float) (evt.getAmount() - evt.getAmount() * bonusLevel * 0.125));
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            int bonusLevel = 0;
            for (ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            if (bonusLevel > 0) {
                evt.setAmount((float) (evt.getAmount() - evt.getAmount() * bonusLevel * 0.25));
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 20 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) &&
                !ench.equals(Enchantments.PROTECTION) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE);
    }

}
