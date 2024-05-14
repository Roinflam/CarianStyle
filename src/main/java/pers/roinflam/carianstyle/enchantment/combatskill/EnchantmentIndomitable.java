package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentIndomitable extends VeryRaryBase {

    public EnchantmentIndomitable(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "indomitable");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.INDOMITABLE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    if (RandomUtil.percentageChance((1 - hurter.getHealth() / hurter.getMaxHealth()) * 100 * 0.75)) {
                        evt.setCanceled(true);
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
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench) &&
                !ench.equals(Enchantments.PROTECTION) &&
                !ench.equals(Enchantments.FIRE_PROTECTION) &&
                !ench.equals(Enchantments.PROJECTILE_PROTECTION) &&
                !ench.equals(Enchantments.BLAST_PROTECTION);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
