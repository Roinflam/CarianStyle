package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
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

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.dark_abandoned_child.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "dark_abandoned_child";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 夜间受到伤害的倍率
     * <p>默认 0.9，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle NIGHT_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "night_damage_multiplier",
                    0.9D, 0.0D, 2.0D);

    /**
     * 夜间每秒回复的最大生命占比
     * <p>默认 0.015，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND =
            EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.015D, 0.0D, 1.0D);


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
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        if (!isFullyCharged(ctx.getHolder())) {
            return;
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

        // v-cache：走中央装备缓存
        int level = EnchantmentEventHandler.mainHand(victim, darkAbandonedChild);

        if (level > 0 && !victim.level().isDay()) {
            evt.setAmount(evt.getAmount() * (float) NIGHT_DAMAGE_MULTIPLIER.get());
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

        // v-cache：走中央装备缓存
        int level = EnchantmentEventHandler.mainHand(player, darkAbandonedChild);

        if (level > 0) {
            player.heal(player.getMaxHealth() * (float) HEAL_RATIO_PER_SECOND.get() / 20);
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
