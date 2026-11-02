package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * PORT-STUB(E3)
 *
 * 腐败翼剑附魔
 * <p>
 * 武器附魔，连击系统
 * 每次攻击增加连击数（最多20）
 * 伤害加成 = 原伤害 × (连击数/4) × 3% × 等级
 * 15秒后连击数开始逐个衰减
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Deprecated
public class EnchantmentCorruptedWingSword extends EnchantmentBase {
    private static final String VALUE_ID = "corrupted_wing_sword";
    private static final EnchantmentValues.Handle MAX_COMBO = EnchantmentValues.define(VALUE_ID, "max_combo",
                    20, 1, 200);
    private static final EnchantmentValues.Handle DECAY_DELAY = EnchantmentValues.define(VALUE_ID, "decay_delay",
                    300, 20, 12000);
    private static final EnchantmentValues.Handle COMBO_DIVISOR = EnchantmentValues.define(VALUE_ID, "combo_divisor",
                    4.0D, 0.5D, 100.0D);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.03D, 0.0D, 2.0D);
    private static final String COMBO_COUNTER_KEY = "corrupted_wing_sword_combo";
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

