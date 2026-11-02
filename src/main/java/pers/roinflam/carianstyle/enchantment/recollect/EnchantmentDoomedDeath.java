package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeInstance;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.util.BossUtil;

/**
 * PORT-STUB(E2)
 *
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
@Deprecated
public class EnchantmentDoomedDeath extends EnchantmentBase {
    private static final String VALUE_ID = "doomed_death";
    private static final EnchantmentValues.Handle DOT_DURATION = EnchantmentValues.define(VALUE_ID, "dot_duration",
                    40, 1, 1200);
    private static final EnchantmentValues.Handle DOT_DELAY = EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle BURNING_SECONDS = EnchantmentValues.define(VALUE_ID, "burning_seconds",
                    2, 1, 120);
    private static final EnchantmentValues.Handle DOOM_SECONDS = EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    45, 1, 120);
    private static final EnchantmentValues.Handle DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.2D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle VICTIM_MAX_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "victim_max_health_ratio",
                    0.02D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle MAX_HEALTH_REDUCTION = EnchantmentValues.define(VALUE_ID, "max_health_reduction",
                    0.1D, 0.0D, 0.9D);
    private static final EnchantmentValues.Handle BOSS_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "boss_multiplier",
                    0.5D, 0.0D, 1.0D);
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

