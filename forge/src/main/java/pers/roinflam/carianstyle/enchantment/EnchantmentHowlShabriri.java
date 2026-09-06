package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 沙布里里嚎叫附魔
 * <p>
 * 武器附魔，叠层诅咒系统
 * 攻击时：
 * - 给目标叠加沙布里里嚎叫效果（持续 = 等级 × 3秒，层数+1，最高5层）
 * - 目标满5层时，伤害增加 15% × 等级
 * - 攻击者自身受到癫火伤害（3秒内共5%最大生命值，创造模式免疫）
 * </p>
 * <p>
 * 性能优化 v3.0：自损持续伤害改用 DamageOverTimeManager
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "howl_shabriri",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentHowlShabriri extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.howl_shabriri.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "howl_shabriri";

    /**
     * 自损持续时间（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_dot_duration",
                    60, 1, 1200);

    /**
     * 自损起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "self_dot_delay",
                    5, 0, 200);

    /**
     * 疯狂叠层的最高等级
     * <p>默认 5，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    5, 0, 127);

    /**
     * 每级的额外伤害倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);

    /**
     * 自损总量占最大生命的比例
     * <p>默认 0.05，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.05D, 0.0D, 1.0D);

    /**
     * 每级叠层的持续秒数
     * <p>默认 3，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle STACK_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "stack_seconds_per_level",
                    3, 1, 120);

    public EnchantmentHowlShabriri() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (attacker instanceof Player) {
            if (((Player) attacker).getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        // 获取当前沙布里里嚎叫等级
        int currentAmplifier = DynamicAttributeManager.getAmplifier(victim, DynamicAttributes.HOWL_SHABRIRI);

        if (currentAmplifier >= MAX_AMPLIFIER.getInt()) {
            // 满5层，增加伤害
            float bonusDamage = ctx.getDamage() * level * (float) BONUS_DAMAGE_PER_LEVEL.get();
            ctx.addDamage(bonusDamage);
        }

        // 叠加沙布里里嚎叫效果（最高5层）
        int newAmplifier = currentAmplifier < 0 ? 0 : Math.min(currentAmplifier + 1, MAX_AMPLIFIER.getInt());
        DynamicAttributeManager.apply(victim,
                DynamicAttributes.HOWL_SHABRIRI.createInstance(level * STACK_SECONDS_PER_LEVEL.getInt() * 20, newAmplifier));

        // 对攻击者造成癫火伤害（创造模式玩家免疫）
        if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
            // 应用火焰燃烧视觉效果
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(3 * 20 + 5, 0));
            ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

            // v3.0优化：自损 5%最大生命值 / 60tick
            float selfDamagePerTick = attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get() / SELF_DOT_DURATION.getInt();
            DamageOverTimeManager.applyLinear(
                    attacker, selfDamagePerTick, SELF_DOT_DURATION.getInt(), SELF_DOT_DELAY.getInt(),
                    NewDamageSource.epilepsyFire(attacker.level()), true
            );
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
