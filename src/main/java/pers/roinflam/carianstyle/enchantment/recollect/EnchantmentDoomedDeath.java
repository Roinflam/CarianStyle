package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeInstance;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.util.BossUtil;

/**
 * 注定死亡附魔
 * <p>
 * 攻击时施加诅咒效果，并造成持续递增伤害
 * 持续40tick，伤害随时间递增，足以致死时直接击杀
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(5, 1)
 * </p>
 * <p>
 * v3.1 数值照艾尔登法环原作「命定之死」重定：原作是 2 秒内扣 2% 最大生命 + 20、最大生命 -10%。
 * 旧数值（5 秒内 50% 本次伤害 + 10% 当前生命、最大生命 -25%）明显过强：
 * 持续伤害按护甲前的伤害算、真伤扣血，每下独立叠加；-25% 的最大生命又会被原版压血，
 * 满血目标挨第一下就直接少四分之一。现在：
 * </p>
 * <ul>
 *     <li>持续伤害 = 2% 最大生命 + 20% 本次伤害，2 秒扣完，每下仍独立叠加；</li>
 *     <li>最大生命 -10%，持续 45 秒（原作值），不叠加（和死亡之刃共用同一个效果）；</li>
 *     <li>目标是 BOSS（{@link BossUtil#isBoss}）时持续伤害减半、最大生命降低不变，
 *     对应原作 DLC BOSS 只对命定之死的百分比掉血有抗性。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 38,
        levelMultiplier = 0
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.doomed_death.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "doomed_death";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    40, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 死亡燃烧视觉的持续秒数
     * <p>默认 2（与持续伤害同时结束），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle BURNING_SECONDS =
            EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    2, 1, 120);

    /**
     * 死亡标记（最大生命降低）的持续秒数
     * <p>默认 45（原作命定之死、死亡刀刃都是 45 秒），允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DOOM_SECONDS =
            EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);

    /**
     * 按本次伤害计算的持续伤害基数比例
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.2D, 0.0D, 5.0D);

    /**
     * 按目标最大生命计算的持续伤害基数比例（v3.1 起取代按当前生命的 victim_health_ratio）
     * <p>默认 0.02，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_MAX_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "victim_max_health_ratio",
                    0.02D, 0.0D, 1.0D);

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

    public EnchantmentDoomedDeath() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null || victim.level().isClientSide) {
            return;
        }

        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        float multiplier = BossUtil.isBoss(victim) ? (float) BOSS_MULTIPLIER.get() : 1.0F;

        // 先按挂减益之前的最大生命算持续伤害总量，再挂最大生命降低
        int duration = DOT_DURATION.getInt();
        float totalDamage = (ctx.getDamage() * (float) DAMAGE_RATIO.get()
                + victim.getMaxHealth() * (float) VICTIM_MAX_HEALTH_RATIO.get()) * multiplier;
        float baseDamagePerTick = totalDamage / duration;

        // 应用注定死亡燃烧效果（猩红色火焰视觉）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(BURNING_SECONDS.getInt() * 20 + 5, 0)
        );

        // 应用注定死亡效果（最大生命降低，BOSS 也一样）
        DynamicAttributeInstance doom = DynamicAttributes.doomedDeath(
                DOOM_SECONDS.getInt() * 20 + 5, MAX_HEALTH_REDUCTION.get());
        if (doom != null) {
            DynamicAttributeManager.apply(victim, doom);
        }

        // 递增伤害：base × 0.3 + base × elapsed / ((duration + 1) / 2) × 0.7。
        // elapsed 从 1 数到 duration，平均正好是 (duration + 1) / 2，所以总量恰好是 base × duration。
        // 以前写死除以 50，只在 100 tick 时总量对得上，改成 40 tick 会少四成
        float halfSpan = (duration + 1) / 2.0F;
        DamageOverTimeManager.applyScaling(
                victim,
                baseDamagePerTick,
                duration,
                DOT_DELAY.getInt(),
                ctx.getDamageSource(),
                true,
                (base, elapsed) -> base * 0.3f + base * elapsed / halfSpan * 0.7f
        );
    }

}
