package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 熔炉之羽附魔
 * <p>
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时：
 * - 受到的伤害增加50% * 附魔等级
 * - 大幅增加无敌帧（invulnerableTime = max + max/2 × 等级 × 1.5）
 * - 获得速度效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * - 获得跳跃提升效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentFurnaceFeather extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.furnace_feather.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "furnace_feather";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级延长无敌帧的系数
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle INVULNERABLE_FACTOR =
            EnchantmentValues.define(VALUE_ID, "invulnerable_factor",
                    1.5D, 0.0D, 10.0D);

    /**
     * 每级施加效果的持续时间（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "effect_ticks_per_level",
                    40, 1, 1200);

    /**
     * 每级的伤害倍率加成
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.5D, 0.0D, 5.0D);

    public EnchantmentFurnaceFeather() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 受击后获得增益效果（最低优先级）
     * 注意：这里使用受害者视角的 onDamageAsVictimLowest
     */
    @Override
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        // 手动应用等级限制（虽然 EnchantmentBase 已经限制过了，但为了保险再限制一次）
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 增加无敌帧
        victim.invulnerableTime = (int) (victim.invulnerableDuration +
                victim.invulnerableDuration / 2.0 * effectiveLevel * INVULNERABLE_FACTOR.get());

        // 添加速度效果
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL.getInt(),
                effectiveLevel - 1
        ));

        // 添加跳跃提升效果
        victim.addEffect(new MobEffectInstance(
                MobEffects.JUMP,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL.getInt(),
                effectiveLevel - 1
        ));

        ctx.multiplyDamage(1 + (float) DAMAGE_PER_LEVEL.get() * level);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
