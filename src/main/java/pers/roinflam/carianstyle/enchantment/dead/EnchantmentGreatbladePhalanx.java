package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E2)
 * <p>巨剑方阵附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "greatblade_phalanx";

    /**
     * 巨剑数量（发射方位自动均分整圆）
     * <p>默认 3，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle BLADE_COUNT =
            EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);

    /**
     * 环形展开半径（格）
     * <p>默认 4.0，允许范围 0.5 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle RING_RADIUS =
            EnchantmentValues.define(VALUE_ID, "ring_radius",
                    4.0D, 0.5D, 32.0D);

    /**
     * 环形离地高度（格）
     * <p>默认 4.5，允许范围 0.0 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle RING_HEIGHT =
            EnchantmentValues.define(VALUE_ID, "ring_height",
                    4.5D, 0.0D, 32.0D);

    /**
     * 触发冷却时间（tick）
     * <p>默认 6000，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "cooldown",
                    6000, 20, 144000);

    /**
     * 第一把巨剑的发射延迟（tick）
     * <p>默认 75，允许范围 0 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle BASE_DELAY =
            EnchantmentValues.define(VALUE_ID, "base_delay",
                    75, 0, 600);

    /**
     * 相邻两把巨剑的发射间隔（tick）
     * <p>默认 25，允许范围 0 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle DELAY_STEP =
            EnchantmentValues.define(VALUE_ID, "delay_step",
                    25, 0, 300);

    /**
     * 每级按已损失生命计算的巨剑伤害比例
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);

    /**
     * 巨剑的追踪强度
     * <p>默认 0.08，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRACKING_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.08D, 0.0D, 1.0D);

    /**
     * 巨剑的最大存活时间（tick）
     * <p>默认 120，允许范围 20 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle MAX_LIFETIME =
            EnchantmentValues.define(VALUE_ID, "max_lifetime",
                    120, 20, 1200);

    /**
     * 巨剑环半径（格）。
     * <p>原实现是 ±10（跨度 20 格），巨剑会飞出视野。巨剑 {@code size=7.5}，
     * 4 格半径下三把剑呈 120° 分布、彼此不穿模，且全部在死者视野内。</p>
     */
    public EnchantmentGreatbladePhalanx() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
