package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 圣地附魔
 * <p>v2.2：受击减伤光环入口接入怪物附魔触发开关。
 * PlayerTickEvent 仅玩家触发，无需开关检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends EnchantmentBase {

    public EnchantmentHolyGround() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    /**
     * 减伤光环：附近有人举着此附魔盾牌时，受击者获得减伤
     */
    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受益者是受击者，怪物受益等同于触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                16,
                entity -> entity.getClass() == victim.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            if (!entity.isUsingItem()) {
                continue;
            }

            ItemStack activeItem = entity.getItemInHand(entity.getUsedItemHand());
            if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
                continue;
            }

            int level = EnchantmentHelper.getItemEnchantmentLevel(holyGround, activeItem);

            if (level > 0) {
                evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.05f);
            }
        }
    }

    /**
     * 治疗/护盾光环（PlayerTickEvent，玩家专属）
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (evt.player.tickCount % 60 != 0) {
            return;
        }

        Player holder = evt.player;
        if (!holder.isAlive()) {
            return;
        }

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(holyGround, activeItem);
        if (level <= 0) {
            return;
        }

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                holder,
                16,
                entity -> entity.getClass() == holder.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            boolean effectApplied = false;

            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(entity.getMaxHealth() * level * 0.015f);
                effectApplied = true;
            }

            float maxAbsorption = entity.getMaxHealth() / 3 * level;
            if (entity.getAbsorptionAmount() < maxAbsorption) {
                float newAbsorption = Math.min(
                        entity.getAbsorptionAmount() + entity.getMaxHealth() * level * 0.03f,
                        maxAbsorption
                );
                entity.setAbsorptionAmount(newAbsorption);
                effectApplied = true;
            }

            if (effectApplied) {
                entity.playSound(SoundEvents.PLAYER_LEVELUP, 1, 3);
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
