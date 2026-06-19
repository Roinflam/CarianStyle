package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 冻结地震附魔
 * <p>v2.2：LivingDamage受击者重击触发入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends EnchantmentBase {

    private static final int MAX_SEARCH_RADIUS = 10;
    private static final int MAX_TARGETS = 20;

    public EnchantmentFreezingEarthquake() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，重击范围冻结）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (evt.getAmount() < victim.getMaxHealth() * 0.25f) {
            return;
        }

        Enchantment freezingEarthquake = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFreezingEarthquake.class);
        if (freezingEarthquake == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(freezingEarthquake, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        int range = 3 + (totalLevel - 1) * 2;
        int searchRadius = Math.min(range, MAX_SEARCH_RADIUS);

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> entity.onGround() && !entity.equals(victim)
        );

        final int effectiveLevel = totalLevel;

        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }

            if (target.onGround()) {
                target.setDeltaMovement(
                        target.getDeltaMovement().x,
                        effectiveLevel * 0.35f,
                        target.getDeltaMovement().z
                );

                target.addEffect(new MobEffectInstance(
                        CarianStylePotion.FROSTBITE.get(),
                        effectiveLevel * 5 * 20,
                        effectiveLevel - 1
                ));
                hitCount++;
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
