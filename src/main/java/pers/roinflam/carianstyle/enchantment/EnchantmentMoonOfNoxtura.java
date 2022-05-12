package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends Enchantment {

    public EnchantmentMoonOfNoxtura(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "moon_of_noxtura");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.MOON_OF_NOXTURA;
    }

    @SubscribeEvent
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityLiving && evt.getTarget() != null) {
                EntityLivingBase target = evt.getTarget();
                EntityLiving entityLiving = (EntityLiving) evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : target.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    if (RandomUtil.percentageChance(2.5)) {
                        double distance = entityLiving.getDistance(target);
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                entityLiving,
                                distance,
                                distance,
                                entity -> entity instanceof EntityMob && entity.getClass() != (entityLiving.getClass()) && entityLiving.canEntityBeSeen(entity) && !entity.equals(entityLiving) && !entity.equals(target)
                        );
                        if (!entities.isEmpty()) {
                            entityLiving.setAttackTarget((EntityLivingBase) entities.get(RandomUtil.getInt(0, entities.size() - 1)));
                        }
                    }
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
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 35;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
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
