package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E2)
 *
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
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentDarkAbandonedChild extends EnchantmentBase {
    private static final String VALUE_ID = "dark_abandoned_child";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle NIGHT_DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "night_damage_multiplier",
                    0.9D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_RATIO_PER_SECOND = EnchantmentValues.define(VALUE_ID, "heal_ratio_per_second",
                    0.015D, 0.0D, 1.0D);
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentDarkAbandonedChild() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
}

