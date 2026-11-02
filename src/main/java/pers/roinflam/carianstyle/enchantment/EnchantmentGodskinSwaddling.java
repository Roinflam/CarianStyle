package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 *
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
@Deprecated
public class EnchantmentGodskinSwaddling extends EnchantmentBase {
    private static final String VALUE_ID = "godskin_swaddling";
    private static final EnchantmentValues.Handle COUNTER_EXPIRY = EnchantmentValues.define(VALUE_ID, "counter_expiry",
                    6000, 20, 72000);
    private static final EnchantmentValues.Handle TRIGGER_COUNT = EnchantmentValues.define(VALUE_ID, "trigger_count",
                    3, 1, 50);
    private static final EnchantmentValues.Handle BASE_HEAL_RATIO = EnchantmentValues.define(VALUE_ID, "base_heal_ratio",
                    0.03D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_PER_EXTRA_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_extra_level",
                    0.01D, 0.0D, 1.0D);
    private static final String ATTACK_COUNTER = "godskin_swaddling_attack";
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

