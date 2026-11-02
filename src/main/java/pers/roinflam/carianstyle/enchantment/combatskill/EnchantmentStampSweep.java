package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>箭步回旋斩附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "stamp_sweep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentStampSweep extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "stamp_sweep";

    /**
     * 单次横扫的最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 横扫半径（格）
     * <p>默认 3.0，允许范围 1.0 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle SWEEP_RADIUS =
            EnchantmentValues.define(VALUE_ID, "sweep_radius",
                    3.0D, 1.0D, 32.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的横扫额外伤害倍率
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);

    /**
     * 正在结算 AOE 时置位（v2.4）。
     * <p>AOE 循环里的 {@code target.hurt} 会同步触发新的 LivingDamageEvent 并回到本附魔，
     * 置位期间直接放行，保证一次冲刺斩只放一圈。只在服务端线程读写，用 ThreadLocal 是为了不和别的线程互相干扰。</p>
     */
    private static final ThreadLocal<Boolean> IN_SWEEP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentStampSweep() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
