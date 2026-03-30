package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
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
 * 暗月附魔 - 修复: 全部3处getUsedItemHand -> InteractionHand.MAIN_HAND / getMainHandItem
 * @version 2.1
 */
@AutoRegisterEnchantment(id = "dark_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class})
@Mod.EventBusSubscriber
public class EnchantmentDarkMoon extends EnchantmentBase {
    public EnchantmentDarkMoon() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    private static boolean hasFullMoonEnchantment(@NotNull LivingEntity entity) {
        Enchantment fullMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentFullMoon.class);
        if (fullMoon == null) return false;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(fullMoon, armor) > 0) return true;
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) return;
        LivingEntity victim = evt.getEntity();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) return;

        // 受击者视角（减伤）- 修复：使用主手
        if (victim instanceof Mob livingVictim) {
            ItemStack heldItem = livingVictim.getItemInHand(InteractionHand.MAIN_HAND);
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
                if (ConfigLoader.levelLimit) level = Math.min(level, 10);
                if (level > 0) {
                    boolean hasFullMoon = hasFullMoonEnchantment(livingVictim);
                    evt.setAmount(evt.getAmount() * (1 - (hasFullMoon ? 0.375f : 0.25f)));
                }
            }
        }

        // 攻击者视角（增伤+吸血）- 修复：使用主手
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity attacker)) return;
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        if (attacker instanceof Player && ((Player) attacker).getAttackStrengthScale(0.5f) != 1) return;
        boolean hasFullMoon = hasFullMoonEnchantment(attacker);
        float damageBonus = hasFullMoon ? 0.375f : 0.25f;
        evt.setAmount(evt.getAmount() * (1 + damageBonus));
        if (victim instanceof Mob livingVictim && livingVictim.getTarget() != null && livingVictim.getTarget().equals(attacker)) {
            float extraDamage = hasFullMoon ? 0.075f : 0.05f;
            evt.setAmount(evt.getAmount() + livingVictim.getHealth() * extraDamage);
            attacker.heal(Math.min(evt.getAmount() * extraDamage, attacker.getMaxHealth() * extraDamage));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide || evt.getEntity().level().isDay()) return;
        LivingEntity healer = evt.getEntity();
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null) return;
        // 修复：使用主手
        ItemStack heldItem = healer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        boolean hasFullMoon = hasFullMoonEnchantment(healer);
        evt.setAmount(evt.getAmount() * (1 + (hasFullMoon ? 0.375f : 0.25f)));
    }

    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.player.level().isDay() || evt.phase != TickEvent.Phase.START) return;
        Player player = evt.player;
        Enchantment darkMoon = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkMoon.class);
        if (darkMoon == null || !player.isAlive()) return;
        // 修复：使用主手
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(darkMoon, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level > 0) player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 210));
    }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
