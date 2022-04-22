package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.java.random.RandomUtil;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;
import pers.roinflam.kaliastyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends Enchantment {

    public EnchantmentMoonOfNoxtura(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "moon_of_noxtura");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.MOON_OF_NOXTURA;
    }

    @SubscribeEvent
    public static void onLivingSetAttackTarget(LivingSetAttackTargetEvent evt) {
        if (!evt.getEntity().world.isRemote && !evt.getEntity().world.isDaytime()) {
            if (evt.getEntityLiving() instanceof EntityMob && evt.getTarget() != null) {
                EntityLivingBase target = evt.getTarget();
                EntityMob entityLivingBase = (EntityMob) evt.getEntityLiving();
                int bonusLevel = 0;
                for (ItemStack itemStack : target.getArmorInventoryList()) {
                    if (itemStack != null) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                if (bonusLevel > 0) {
                    if (RandomUtil.percentageChance(2.5)) {
                        double distance = entityLivingBase.getDistance(target);
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                entityLivingBase,
                                distance,
                                distance,
                                entity -> entity instanceof EntityMob && entity.getClass() != (entityLivingBase.getClass()) && entityLivingBase.canEntityBeSeen(entity) && !entity.equals(entityLivingBase) && !entity.equals(target)
                        );
                        if (!entities.isEmpty()) {
                            entityLivingBase.setAttackTarget((EntityLivingBase) entities.get(RandomUtil.getInt(0, entities.size() - 1)));
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
        return !ench.equals(KaliaStyleEnchantments.HEALING_BY_FIRE) &&
                !ench.equals(KaliaStyleEnchantments.SHELTER_OF_FIRE) &&
                !ench.equals(KaliaStyleEnchantments.PRECISE_LIGHTNING) &&
                !ench.equals(KaliaStyleEnchantments.ANCIENT_DRAGON_LIGHTNING);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
