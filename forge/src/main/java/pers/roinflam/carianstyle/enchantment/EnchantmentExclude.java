package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 排斥附魔
 * <p>
 * 护腿附魔，受击时范围击退
 * 受到攻击时击退周围所有敌人
 * 范围 = 5 + (等级 - 1) × 0.75格
 * </p>
 * <p>v2.2：受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "exclude",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_LEGS,
        slots = {EquipmentSlot.LEGS}
)
@Mod.EventBusSubscriber
public class EnchantmentExclude extends EnchantmentBase {

    private static final double MAX_SEARCH_RADIUS = 10.0;
    private static final int MAX_TARGETS = 20;

    public EnchantmentExclude() {
        super(EnchantmentCategory.ARMOR_LEGS, new EquipmentSlot[]{EquipmentSlot.LEGS});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment exclude = EnchantmentRegistry.getEnchantmentByClass(EnchantmentExclude.class);

        if (exclude == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(exclude, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        double range = 5 + (totalLevel - 1) * 0.75;
        double searchRadius = Math.min(range, MAX_SEARCH_RADIUS);

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(victim)
        );

        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }
            double x = victim.getX() - target.getX();
            double z = victim.getZ() - target.getZ();
            target.knockback(0.5f, x, z);
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
