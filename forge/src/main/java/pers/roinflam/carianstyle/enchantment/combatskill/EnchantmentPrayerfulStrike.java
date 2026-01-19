// 文件：EnchantmentPrayerfulStrike.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentPrayerfulStrike.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import java.util.UUID;

/**
 * 祈祷一击附魔
 * <p>
 * 攻击后开始蓄力（8秒），4秒后进入准备状态
 * 准备状态下攻击触发：额外伤害、增加最大生命值、治疗自身
 * 蓄力期间每秒播放音效提示
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "prayerful_strike",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true,
        conflictsWith = {EnchantmentScarletCorruption.class}
)
@Mod.EventBusSubscriber
public class EnchantmentPrayerfulStrike extends EnchantmentBase {

    private static final UUID MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");
    private static final String MAX_HEALTH_MODIFIER_NAME = "enchantment.prayerful_strike";

    private static final String CHARGING_COOLDOWN_KEY = "prayerful_strike_charging";
    private static final String NOT_READY_COOLDOWN_KEY = "prayerful_strike_not_ready";

    public EnchantmentPrayerfulStrike() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 伤害事件处理（LOWEST优先级）
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        Enchantment prayerfulStrike = EnchantmentRegistry.getEnchantmentByClass(EnchantmentPrayerfulStrike.class);
        if (prayerfulStrike == null) {
            return;
        }

        // 攻击者视角
        ItemStack attackerHeld = attacker.getItemInHand(attacker.getUsedItemHand());
        if (!attackerHeld.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(prayerfulStrike, attackerHeld);

            if (level > 0) {
                handleAttackerLogic(evt, attacker, victim);
            }
        }

        // 受击者视角
        ItemStack victimHeld = victim.getItemInHand(victim.getUsedItemHand());
        if (!victimHeld.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(prayerfulStrike, victimHeld);

            if (level > 0) {
                handleVictimLogic(victim);
            }
        }
    }

    private static void handleAttackerLogic(LivingDamageEvent evt, LivingEntity attacker, LivingEntity victim) {
        UUID uuid = attacker.getUUID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod) {
            if (!stillWaiting) {
                triggerPrayerfulStrike(evt, attacker, victim);
                EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
                EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
            }
        } else {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        }
    }

    private static void handleVictimLogic(LivingEntity victim) {
        UUID uuid = victim.getUUID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && !stillWaiting) {
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        } else if (!inChargingPeriod) {
            EnchantmentDataManager.setCooldown(CHARGING_COOLDOWN_KEY, uuid, 160);
            EnchantmentDataManager.setCooldown(NOT_READY_COOLDOWN_KEY, uuid, 80);
        }
    }

    private static void triggerPrayerfulStrike(LivingDamageEvent evt, LivingEntity attacker, LivingEntity victim) {
        float bonusDamage = Math.min(
                attacker.getMaxHealth() * 0.025f + victim.getHealth() * 0.075f,
                victim.getMaxHealth() * 0.1f
        );
        evt.setAmount(evt.getAmount() + bonusDamage);

        applyMaxHealthBonus(attacker, bonusDamage);
        attacker.heal(bonusDamage);

        attacker.playSound(SoundEvents.PLAYER_LEVELUP, 1, 0);
        victim.playSound(SoundEvents.PLAYER_LEVELUP, 1, 0);
    }

    private static void applyMaxHealthBonus(LivingEntity attacker, float bonusDamage) {
        // 1.20.1: getEntityAttribute → getAttribute
        AttributeInstance attribute = attacker.getAttribute(Attributes.MAX_HEALTH);
        if (attribute == null) {
            return;
        }

        float healthBonus = bonusDamage / 2;
        float maxBonus = attacker.getMaxHealth() * 0.05f;
        AttributeModifier existing = attribute.getModifier(MAX_HEALTH_MODIFIER_ID);

        if (existing == null) {
            attribute.addPermanentModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID,
                    MAX_HEALTH_MODIFIER_NAME,
                    healthBonus,
                    AttributeModifier.Operation.ADDITION
            ));
        } else {
            double newAmount = Math.min(
                    existing.getAmount() + Math.min(healthBonus, maxBonus),
                    ConfigLoader.prayerfulStrikeMaxHealth
            );
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
            attribute.addPermanentModifier(new AttributeModifier(
                    MAX_HEALTH_MODIFIER_ID,
                    MAX_HEALTH_MODIFIER_NAME,
                    newAmount,
                    AttributeModifier.Operation.ADDITION
            ));
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(@NotNull PlayerEvent.PlayerRespawnEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.isEndConquered()) {
            return;
        }

        AttributeInstance attribute = evt.getEntity().getAttribute(Attributes.MAX_HEALTH);
        if (attribute != null) {
            attribute.removeModifier(MAX_HEALTH_MODIFIER_ID);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        UUID uuid = evt.getEntity().getUUID();
        EnchantmentDataManager.clearCooldown(CHARGING_COOLDOWN_KEY, uuid);
        EnchantmentDataManager.clearCooldown(NOT_READY_COOLDOWN_KEY, uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.START || evt.player.tickCount % 20 != 0) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        UUID uuid = player.getUUID();
        boolean inChargingPeriod = EnchantmentDataManager.isOnCooldown(CHARGING_COOLDOWN_KEY, uuid);
        boolean stillWaiting = EnchantmentDataManager.isOnCooldown(NOT_READY_COOLDOWN_KEY, uuid);

        if (inChargingPeriod && stillWaiting) {
            player.playSound(SoundEvents.PLAYER_LEVELUP, 1, 3);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}