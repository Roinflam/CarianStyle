package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeInstance;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.BossUtil;

import javax.annotation.Nonnull;

/**
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
 * <p>
 * 1.12.2 版：「无视无敌」的伤害就是 {@code canHarmInCreative()}（虚空、/kill 这类），{@link DamageTags} 按开关推出这个标签；
 * 持续伤害致死的那一下由 {@link DamageOverTimeManager} 换成绝对伤害源结算，抗性提升 V 下也烧得死。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@AutoRegisterEnchantment(
        id = "death_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentDeathBlade extends EnchantmentBase {
    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.death_blade.desc，
    //   否则玩家看到的描述会与实际效果不符。


    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "death_blade";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 100，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    100, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 1，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    1, 0, 200);

    /**
     * 转为持续伤害的比例（以本次伤害为基数）
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DOT_RATIO =
            EnchantmentValues.define(VALUE_ID, "dot_ratio",
                    0.5D, 0.0D, 5.0D);

    /**
     * 最大生命降低比例
     * <p>默认 0.1，允许范围 0.0 ~ 0.9。</p>
     */
    private static final EnchantmentValues.Handle MAX_HEALTH_REDUCTION =
            EnchantmentValues.define(VALUE_ID, "max_health_reduction",
                    0.1D, 0.0D, 0.9D);

    /**
     * 目标是 BOSS 时持续伤害的倍率（最大生命降低不受影响）
     * <p>默认 0.5，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle BOSS_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "boss_multiplier",
                    0.5D, 0.0D, 1.0D);

    /**
     * 直接伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DIRECT_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "direct_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 死亡燃烧视觉的持续秒数
     * <p>默认 5，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle BURNING_SECONDS =
            EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    5, 1, 120);

    /**
     * 死亡标记（最大生命降低）的持续秒数
     * <p>默认 45（原作命定之死、死亡刀刃都是 45 秒），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DOOM_SECONDS =
            EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);

    public EnchantmentDeathBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        if (damageSource == null || "deathBlade".equals(damageSource.getDamageType()) ||
                DamageTags.is(damageSource, DamageTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (ctx.getVictim() == null) {
            return;
        }

        // v3.1：最大生命减益与这一下打多少无关，连点能飞快给一群目标挂上，要求满蓄力。
        // 未满蓄力的一下拿不到持续伤害和最大生命减益，但即时伤害折半这个代价照样付，
        // 否则连点的即时伤害（×1.0）反而比满蓄力（×0.5 + 持续伤害）更高，收残血时更划算
        if (!isFullyCharged(ctx.getHolder())) {
            ctx.multiplyDamage((float) DIRECT_MULTIPLIER.get());
            return;
        }

        float multiplier = BossUtil.isBoss(ctx.getVictim()) ? (float) BOSS_MULTIPLIER.get() : 1.0F;

        // 计算每tick伤害（按折半之前的本次伤害，BOSS 减半）
        float damagePerTick = ctx.getDamage() * (float) DOT_RATIO.get() * multiplier / DOT_DURATION.getInt();

        // 降低即时伤害为50%
        ctx.multiplyDamage((float) DIRECT_MULTIPLIER.get());

        // 应用火焰燃烧效果（同步客户端渲染）
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(BURNING_SECONDS.getInt() * 20 + 5, 0));
        ClientSyncEffectHelper.onAttributeApplied(ctx.getVictim(), DynamicAttributes.DOOMED_DEATH_BURNING);

        // 应用注定死亡效果（最大生命降低，BOSS 也一样）
        DynamicAttributeInstance doom = DynamicAttributes.doomedDeath(
                DOOM_SECONDS.getInt() * 20 + 5, MAX_HEALTH_REDUCTION.get());
        if (doom != null) {
            DynamicAttributeManager.apply(ctx.getVictim(), doom);
        }

        // 持续伤害
        DamageOverTimeManager.applyLinear(
                ctx.getVictim(),
                damagePerTick,
                DOT_DURATION.getInt(),
                DOT_DELAY.getInt(),
                damageSource,
                true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
