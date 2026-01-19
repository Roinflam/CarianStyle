package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentFullMoon;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 暗月附魔
 * <p>
 * 武器附魔，夜晚魔法伤害强化
 * 功能：
 * 1. 受到魔法伤害时减伤25%（有满月时37.5%）
 * 2. 造成魔法伤害时增伤25%（有满月时37.5%）
 * 3. 对锁定自己为目标的敌人额外增伤并吸血
 * 4. 治疗增强25%（有满月时37.5%）
 * 5. 持续夜视效果
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "dark_moon",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends EnchantmentBase {

    public EnchantmentDarkMoon() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    private static boolean hasFullMoonEnchantment(@NotNull LivingEntity entity) {
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) {
            return false;
        }

        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                if (EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getEntity().level().isDay()) {
            return;
        }

        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        // 受击者视角（减伤）
        if (victim instanceof Mob) {
            Mob livingVictim = (Mob) victim;

            ItemStack heldItem = livingVictim.getItemInHand(livingVictim.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);

                if (ConfigLoader.levelLimit) {
                    level = Math.min(level, 10);
                }

                if (level > 0) {
                    boolean hasFullMoon = hasFullMoonEnchantment(livingVictim);
                    float reduction = hasFullMoon ? 0.375f : 0.25f;
                    evt.setAmount(evt.getAmount() * (1 - reduction));
                }
            }
        }

        // 攻击者视角（增伤+吸血）
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        if (attacker instanceof Player) {
            if (((Player) attacker).getAttackStrengthScale(0.5f) != 1) {
                return;
            }
        }

        boolean hasFullMoon = hasFullMoonEnchantment(attacker);
        float damageBonus = hasFullMoon ? 0.375f : 0.25f;

        evt.setAmount(evt.getAmount() * (1 + damageBonus));

        if (victim instanceof Mob) {
            Mob livingVictim = (Mob) victim;
            if (livingVictim.getTarget() != null && livingVictim.getTarget().equals(attacker)) {
                float extraDamage = hasFullMoon ? 0.075f : 0.05f;

                evt.setAmount(evt.getAmount() + livingVictim.getHealth() * extraDamage);

                float healAmount = Math.min(evt.getAmount() * extraDamage, attacker.getMaxHealth() * extraDamage);
                attacker.heal(healAmount);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getEntity().level().isDay()) {
            return;
        }

        LivingEntity healer = evt.getEntity();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        ItemStack heldItem = healer.getItemInHand(healer.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        boolean hasFullMoon = hasFullMoonEnchantment(healer);
        float healBonus = hasFullMoon ? 0.375f : 0.25f;
        evt.setAmount(evt.getAmount() * (1 + healBonus));
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide) {
            return;
        }

        if (evt.player.level().isDay()) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = evt.player;
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) {
            return;
        }

        if (!player.isAlive()) {
            return;
        }

        ItemStack heldItem = player.getItemInHand(player.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level > 0) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 210));
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