package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 死亡之刃附魔
 * <p>
 * 武器附魔
 * 初始伤害降为50%，但施加死亡烙印
 * 持续5秒造成累计伤害（总伤害=原伤害×75%）
 * </p>
 * <p>
 * 性能优化 v3.0：使用 DamageOverTimeManager 替代 SynchronizationTask(1, 1)
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "death_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.death_blade.desc，
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
     * <p>默认 0.75，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DOT_RATIO =
            EnchantmentValues.define(VALUE_ID, "dot_ratio",
                    0.75D, 0.0D, 5.0D);

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
     * 死亡标记的持续秒数
     * <p>默认 10，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DOOM_SECONDS =
            EnchantmentValues.define(VALUE_ID, "doom_seconds",
                    10, 1, 120);

    public EnchantmentDeathBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        if (damageSource == null || "deathBlade".equals(damageSource.getMsgId()) ||
                damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (ctx.getVictim() == null) {
            return;
        }

        // 计算每tick伤害
        float damagePerTick = ctx.getDamage() * (float) DOT_RATIO.get() / DOT_DURATION.getInt();

        // 降低即时伤害为50%
        ctx.multiplyDamage((float) DIRECT_MULTIPLIER.get());

        // 应用火焰燃烧效果（同步客户端渲染）
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(BURNING_SECONDS.getInt() * 20 + 5, 0));
        ClientSyncEffectHelper.onAttributeApplied(ctx.getVictim(), DynamicAttributes.DOOMED_DEATH_BURNING);

        // 应用注定死亡效果
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH.createInstance(DOOM_SECONDS.getInt() * 20 + 5, 0));

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
