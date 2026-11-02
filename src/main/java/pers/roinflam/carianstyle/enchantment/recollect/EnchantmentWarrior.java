package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>战士附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "warrior",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentWarrior extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "warrior";

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DURATION =
            EnchantmentValues.define(VALUE_ID, "dot_duration",
                    60, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 输出伤害的倍率
     * <p>默认 1.25，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    1.25D, 0.0D, 10.0D);

    /**
     * 受到伤害的倍率
     * <p>默认 0.5，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "reduction_multiplier",
                    0.5D, 0.0D, 2.0D);

    /**
     * 击杀时回复已损失生命的比例
     * <p>默认 0.25，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle KILL_HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "kill_heal_ratio",
                    0.25D, 0.0D, 2.0D);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /**
     * 本附魔在 {@link DamageOverTimeManager} 中的来源标签（v3.3 新增）。
     * <p>
     * {@code DamageOverTimeManager} 是<b>七个附魔共用的池子</b>（注定死亡、死亡之刃、
     * 黑焰刃、癫火、战士、沙布里里嚎叫、空癫火）。HUD 要显示「战士流血剩余」，
     * 就必须能把本附魔的条目从池子里挑出来——否则一个同时中了癫火的玩家，
     * 那一行会把癫火的伤害也算进来，数字看着有但意义是错的。
     * </p>
     * <p>
     * <b>public 是刻意的：</b>{@code CarianStyleCombatStateDisplay} 直接引用这个常量，
     * 而不是自己硬编码一份同样的字符串——两处字符串迟早会因为一次改名而不同步。
     * </p>
     */
    public static final String DOT_TAG = "warrior";

    public EnchantmentWarrior() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int l) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
