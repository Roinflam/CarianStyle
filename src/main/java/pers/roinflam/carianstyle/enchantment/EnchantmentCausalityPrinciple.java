package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 因果律附魔
 *
 * 护甲附魔，受到5次伤害后触发范围反击
 * 对周围所有敌人造成等同于最后一次伤害×等级×75%的伤害
 * 反击范围 = 等级×3格
 */
@AutoRegisterEnchantment(
        id = "causality_principle",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends EnchantmentBase {

    private static final String COUNTER_KEY = "causality_principle";
    private static final int TRIGGER_COUNT = 5;

    public EnchantmentCausalityPrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (evt.getSource().canHarmInCreative()) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment causalityPrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCausalityPrinciple.class);
        if (causalityPrinciple == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(causalityPrinciple, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        final int effectiveLevel = totalLevel;

        int currentCount = EnchantmentDataManager.incrementCounter(COUNTER_KEY, victim.getUniqueID());

        if (currentCount >= TRIGGER_COUNT) {
            EnchantmentDataManager.resetCounter(COUNTER_KEY, victim.getUniqueID());

            List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                    EntityLivingBase.class,
                    victim,
                    effectiveLevel * 3,
                    entity -> !entity.equals(victim)
            );

            float damage = evt.getAmount() * effectiveLevel * 0.75f;
            for (EntityLivingBase target : targets) {
                target.attackEntityFrom(DamageSource.causeMobDamage(victim), damage);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}