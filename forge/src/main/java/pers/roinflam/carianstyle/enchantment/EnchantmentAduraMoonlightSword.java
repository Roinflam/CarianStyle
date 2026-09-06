package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 阿杜拉月光剑附魔
 * <p>
 * 攻击变为魔法伤害，对目标周围敌人施加冻伤
 * 白天：叠加+1等级，夜晚：叠加+2等级
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止高等级附魔（如 100 级）
 *       直接把等级当半径，导致 100 格搜索扫过大量实体。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下
 *       对大量实体施加效果导致事件风暴。</li>
 * </ul>
 *
 * <p>本附魔每次攻击触发，触发频率极高，必须严格封顶。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "adura_moonlight_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentEpilepsyFire.class,
                EnchantmentEatShit.class,
                EnchantmentHypnoticSmoke.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
public class EnchantmentAduraMoonlightSword extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.adura_moonlight_sword.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "adura_moonlight_sword";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 6，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    6, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 16，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    16, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 施加冻伤的持续时间（tick）
     * <p>默认 200，允许范围 20 ~ 6000。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_DURATION =
            EnchantmentValues.define(VALUE_ID, "frostbite_duration",
                    200, 20, 6000);

    /**
     * 冻伤可叠加到的最高等级（0 表示 I 级）
     * <p>默认 9，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle FROSTBITE_MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "frostbite_max_amplifier",
                    9, 0, 127);

    public EnchantmentAduraMoonlightSword() {
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
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 玩家需要刚挥剑，非玩家直接触发
        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        // 伤害变为魔法伤害
        if (ctx.getDamageSource() != null) {
            pers.roinflam.carianstyle.utils.util.DamageSourceUtil.setMagicDamage(ctx.getDamageSource());
        }

        // ⭐ v2.1：搜索半径硬上限，防止等级直接当半径
        // 原：effectiveLevel（100级 = 100格）
        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS.getInt());

        // 获取目标周围的敌人
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(attacker)
        );

        // 施加冻伤效果
        boolean isNight = !attacker.level().isDay();
        int stackIncrease = isNight ? 2 : 1;
        int initialLevel = isNight ? 1 : 0;

        // ⭐ v2.1：命中数量硬上限，防止密集怪物场景下事件风暴
        int hitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS.getInt()) {
                break;
            }

            MobEffectInstance existingEffect = entity.getEffect(CarianStylePotion.FROSTBITE.get());

            int newLevel;
            if (existingEffect != null) {
                newLevel = Math.min(existingEffect.getAmplifier() + stackIncrease, FROSTBITE_MAX_AMPLIFIER.getInt());
            } else {
                newLevel = initialLevel;
            }

            entity.addEffect(new MobEffectInstance(CarianStylePotion.FROSTBITE.get(), FROSTBITE_DURATION.getInt(), newLevel));
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
