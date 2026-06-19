package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
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
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 暗弃子附魔
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关。
 * onHurtAsAttacker 走中央事件分发器，已被 scanEntity 拦截。
 * onPlayerTick 玩家专属，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "dark_abandoned_child",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentDarkAbandonedChild() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        if (ctx.getDamageSource() != null) {
            DamageSourceUtil.setBypassesArmor(ctx.getDamageSource());
            DamageSourceUtil.setMagicDamage(ctx.getDamageSource());
        }

        Collection<MobEffectInstance> activeEffects = victim.getActiveEffects();
        if (!activeEffects.isEmpty()) {
            List<MobEffectInstance> positiveEffects = new ArrayList<>(activeEffects);
            positiveEffects.removeIf(effect -> {
                MobEffect mobEffect = effect.getEffect();
                return !mobEffect.isBeneficial() ||
                        mobEffect.isInstantenous() ||
                        !effect.isVisible();
            });

            if (!positiveEffects.isEmpty()) {
                MobEffectInstance stolen = positiveEffects.get(RandomUtil.getInt(0, positiveEffects.size() - 1));
                attacker.addEffect(new MobEffectInstance(stolen));
                victim.removeEffect(stolen.getEffect());
            }
        }
    }

    /**
     * 受击减伤（夜晚10%）
     * <p>v2.1：受击者视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack heldItem = victim.getMainHandItem();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(darkAbandonedChild, heldItem);

        if (level > 0 && !victim.level().isDay()) {
            evt.setAmount(evt.getAmount() * 0.9f);
        }
    }

    /**
     * 夜晚持续回血（PlayerTickEvent玩家专属，无需开关检查）
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.player.level().isDay()) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment darkAbandonedChild = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDarkAbandonedChild.class);
        if (darkAbandonedChild == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(darkAbandonedChild, heldItem);

        if (level > 0) {
            player.heal(player.getMaxHealth() * 0.015f / 20);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
