package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeInstance;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.BossUtil;

/**
 * PORT-STUB(E3)
 *
 * 死亡之刃附魔
 * <p>
 * 武器附魔
 * 初始伤害降为50%，但施加死亡烙印
 * 持续5秒造成累计伤害（总伤害=原伤害×50%）
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(1, 1)
 * </p>
 * <p>
 * v3.1：玩家必须满蓄力才有持续伤害和注定死亡；未满蓄力的一下只付「即时伤害折半」的代价、拿不到好处，怪物持有者不受影响
 * </p>
 * <p>
 * v3.2：数值往艾尔登法环原作「死亡刀刃」（黑刀战技，最大生命 -10%、5 秒持续掉血）靠拢。
 * 旧的持续伤害是 75% 本次伤害（按护甲前的伤害算、真伤扣血），加上折半后的即时伤害合计 125%，
 * 对穿重甲的目标远超普通一刀；最大生命 -25% 又会被原版压血，满血目标挨第一下就少四分之一。现在：
 * 持续伤害 50% 本次伤害，最大生命 -10%（和命定之死共用同一个效果，不叠加）；
 * 目标是 BOSS（{@link BossUtil#isBoss}）时持续伤害减半、最大生命降低不变。即时伤害折半不变。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@Deprecated
public class EnchantmentDeathBlade extends EnchantmentBase {
    private static final String VALUE_ID = "death_blade";
    private static final EnchantmentValues.Handle DOT_DURATION = EnchantmentValues.define(VALUE_ID, "dot_duration",
                    100, 1, 1200);
    private static final EnchantmentValues.Handle DOT_DELAY = EnchantmentValues.define(VALUE_ID, "dot_delay",
                    1, 0, 200);
    private static final EnchantmentValues.Handle DOT_RATIO = EnchantmentValues.define(VALUE_ID, "dot_ratio",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle MAX_HEALTH_REDUCTION = EnchantmentValues.define(VALUE_ID, "max_health_reduction",
                    0.1D, 0.0D, 0.9D);
    private static final EnchantmentValues.Handle BOSS_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "boss_multiplier",
                    0.5D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle DIRECT_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "direct_multiplier",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle BURNING_SECONDS = EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    5, 1, 120);
    private static final EnchantmentValues.Handle DOOM_SECONDS = EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

