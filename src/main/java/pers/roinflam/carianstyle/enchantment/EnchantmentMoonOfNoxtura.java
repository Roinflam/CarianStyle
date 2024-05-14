package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends VeryRaryBase {

    public EnchantmentMoonOfNoxtura(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "moon_of_noxtura");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.MOON_OF_NOXTURA;
    }

    @SubscribeEvent
    public static void onLivingSetAttackTarget(@Nonnull LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityLiving && evt.getTarget() != null) {
                EntityLivingBase target = evt.getTarget();
                EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : target.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    if (RandomUtil.percentageChance(2.5)) {
                        double distance = entityLiving.getDistance(target);
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                entityLiving,
                                (int) distance,
                                entityLivingBase -> entityLivingBase.getClass() != (entityLiving.getClass()) && entityLiving.canEntityBeSeen(entityLivingBase) && !entityLivingBase.equals(entityLiving) && !entityLivingBase.equals(target)
                        );
                        if (!entities.isEmpty()) {
                            entityLiving.setAttackTarget(entities.get(RandomUtil.getInt(0, entities.size() - 1)));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(CarianStyleEnchantments.HEALING_BY_FIRE) &&
                !ench.equals(CarianStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(CarianStyleEnchantments.PRECISE_LIGHTNING) &&
                !ench.equals(CarianStyleEnchantments.ANCIENT_DRAGON_LIGHTNING);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
