package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 刺客赌局附魔
 * <p>v2.2：双向监听器+暴击事件入口接入怪物附魔触发开关。
 * CriticalHitEvent 仅玩家触发，开关检查作为安全网保留。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "assassin_gambit",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentAssassinGambit extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.assassin_gambit.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "assassin_gambit";

    /**
     * 参与计算的等级上限，防止超高等级导致伤害失控
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级对潜行目标的额外伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.25D, 0.0D, 5.0D);

    /**
     * 每级提供的潜行持续时间（tick）
     * <p>默认 20，允许范围 1 ~ 400。</p>
     */
    private static final EnchantmentValues.Handle STEALTH_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stealth_ticks_per_level",
                    20, 1, 400);

    /**
     * 潜行状态下的暴击倍率
     * <p>默认 2.0，允许范围 1.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle CRIT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "crit_multiplier",
                    2.0D, 1.0D, 20.0D);

    public EnchantmentAssassinGambit() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        // 攻击者视角：隐身状态下增伤
        // ⭐ v2.2：怪物附魔触发开关（攻击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) {
            if (DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
                ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
                if (!heldItem.isEmpty()) {
                    int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);
                    if (ConfigLoader.levelLimit) {
                        level = Math.min(level, LEVEL_CAP.getInt());
                    }
                    if (level > 0) {
                        DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
                        evt.setAmount(evt.getAmount() + evt.getAmount() * level * (float) DAMAGE_PER_LEVEL.get());
                    }
                }
            }
        }

        // 受击者视角：获得隐身
        // ⭐ v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        ItemStack victimHeldItem = victim.getItemInHand(InteractionHand.MAIN_HAND);
        if (!victimHeldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, victimHeldItem);
            if (level > 0) {
                DynamicAttributeManager.apply(victim,
                        DynamicAttributes.STEALTH.createInstance(level * STEALTH_TICKS_PER_LEVEL.getInt()));
            }
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(@NotNull CriticalHitEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        if (!evt.isVanillaCritical()) {
            return;
        }
        if (!(evt.getTarget() instanceof LivingEntity)) {
            return;
        }

        Player attacker = evt.getEntity();

        // ⭐ v2.2：CriticalHitEvent 仅玩家触发，开关检查作为安全网（玩家始终放行）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        if (!DynamicAttributeManager.has(attacker, DynamicAttributes.STEALTH)) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment assassinGambit = EnchantmentRegistry.getEnchantmentByClass(EnchantmentAssassinGambit.class);
        if (assassinGambit == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(assassinGambit, heldItem);
        if (level > 0) {
            DynamicAttributeManager.remove(attacker, DynamicAttributes.STEALTH);
            evt.setDamageModifier(evt.getDamageModifier() * (float) CRIT_MULTIPLIER.get());
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
