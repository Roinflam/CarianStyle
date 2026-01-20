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
 * <p>
 * 攻击时：伤害变为魔法且无视护甲，偷取敌人一个正面效果
 * 受击时：夜晚减伤10%
 * 被动：夜晚持续回血
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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

    /**
     * 修复：改用 Normal 优先级
     */
    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        // 设置为魔法伤害且无视护甲
        if (ctx.getDamageSource() != null) {
            DamageSourceUtil.setBypassesArmor(ctx.getDamageSource());
            DamageSourceUtil.setMagicDamage(ctx.getDamageSource());
        }

        // 偷取一个正面效果
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
     * 夜晚持续回血
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