package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 神皮襁褓附魔
 * <p>
 * 武器附魔，连续攻击回血
 * 每攻击4次触发一次治疗：
 * - 治疗量 = 最大生命值 × (3% + (等级 - 1) × 1%)
 * - 等级1: 3%，等级2: 4%，等级3: 5%...
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "godskin_swaddling",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentGodskinSwaddling extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.godskin_swaddling.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "godskin_swaddling";

    /**
     * 攻击计数的保持时长（tick）
     * <p>默认 6000，允许范围 20 ~ 72000。</p>
     */
    private static final EnchantmentValues.Handle COUNTER_EXPIRY =
            EnchantmentValues.define(VALUE_ID, "counter_expiry",
                    6000, 20, 72000);

    /**
     * 触发所需的攻击次数
     * <p>默认 3，允许范围 1 ~ 50。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_COUNT =
            EnchantmentValues.define(VALUE_ID, "trigger_count",
                    3, 1, 50);

    /**
     * 一级时回复的最大生命占比
     * <p>默认 0.03，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "base_heal_ratio",
                    0.03D, 0.0D, 1.0D);

    /**
     * 每超过一级额外回复的最大生命占比
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_EXTRA_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_extra_level",
                    0.01D, 0.0D, 1.0D);


    private static final String ATTACK_COUNTER = "godskin_swaddling_attack";

    public EnchantmentGodskinSwaddling() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        int currentCount = EnchantmentDataManager.getCounter(ATTACK_COUNTER, attacker.getUUID());

        if (currentCount == TRIGGER_COUNT.getInt()) {
            EnchantmentDataManager.resetCounter(ATTACK_COUNTER, attacker.getUUID());

            float healAmount = attacker.getMaxHealth() * (float) BASE_HEAL_RATIO.get()
                + attacker.getMaxHealth() * (level - 1) * (float) HEAL_PER_EXTRA_LEVEL.get();
            attacker.heal(healAmount);
        } else {
            EnchantmentDataManager.incrementCounter(ATTACK_COUNTER, attacker.getUUID(), COUNTER_EXPIRY.getInt());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
