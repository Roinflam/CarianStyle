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
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

@Mod.EventBusSubscriber
public class EnchantmentProtectionOfTheErdtree extends Enchantment {

    public EnchantmentProtectionOfTheErdtree(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "protection_of_the_erdtree");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.PROTECTION_OF_THE_ERDTREE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                int bonusLevel = 0;
                for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    hurter.addPotionEffect(new PotionEffect(CarianStylePotion.PROTECTION_OF_THE_ERDTREE, (int) (2.5 * bonusLevel * 20), bonusLevel - 1));
                }
                bonusLevel = 0;
                for (ItemStack itemStack : attacker.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    attacker.addPotionEffect(new PotionEffect(CarianStylePotion.PROTECTION_OF_THE_ERDTREE, (int) (2.5 * bonusLevel * 20), bonusLevel - 1));
                }
            }
        }
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 35 + (enchantmentLevel - 1) * 25;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
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
