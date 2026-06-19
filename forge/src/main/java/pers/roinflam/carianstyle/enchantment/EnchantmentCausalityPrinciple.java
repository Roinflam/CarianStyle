package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 因果律附魔
 * <p>v2.2：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "causality_principle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends EnchantmentBase {

    private static final int MAX_SEARCH_RADIUS = 10;
    private static final int MAX_TARGETS = 20;
    private static final String COUNTER_KEY = "causality_principle";
    private static final int TRIGGER_COUNT = 5;

    public EnchantmentCausalityPrinciple() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，5次受击触发AOE反击）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment causalityPrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCausalityPrinciple.class);
        if (causalityPrinciple == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(causalityPrinciple, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        final int effectiveLevel = totalLevel;

        int currentCount = EnchantmentDataManager.incrementCounter(COUNTER_KEY, victim.getUUID());

        if (currentCount >= TRIGGER_COUNT) {
            EnchantmentDataManager.resetCounter(COUNTER_KEY, victim.getUUID());

            int searchRadius = Math.min(effectiveLevel * 3, MAX_SEARCH_RADIUS);

            List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    victim,
                    searchRadius,
                    entity -> !entity.equals(victim)
            );

            float damage = evt.getAmount() * effectiveLevel * 0.75f;

            int hitCount = 0;
            for (LivingEntity target : targets) {
                if (hitCount >= MAX_TARGETS) {
                    break;
                }
                target.hurt(victim.damageSources().mobAttack(victim), damage);
                hitCount++;
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
