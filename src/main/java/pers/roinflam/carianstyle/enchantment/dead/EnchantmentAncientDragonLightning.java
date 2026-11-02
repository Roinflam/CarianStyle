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
 * <p>古龙雷电附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "ancient_dragon_lightning";

    /**
     * 单次触发最大命中目标数
     * <p>默认 100，允许范围 1 ~ 500。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    100, 1, 500);

    /**
     * 水平搜索半径（格）
     * <p>默认 60，允许范围 1 ~ 128。</p>
     */
    private static final EnchantmentValues.Handle SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "search_radius",
                    60, 1, 128);

    /**
     * 垂直搜索半高（格）
     * <p>默认 15，允许范围 1 ~ 128。</p>
     */
    private static final EnchantmentValues.Handle SEARCH_HEIGHT =
            EnchantmentValues.define(VALUE_ID, "search_height",
                    15, 1, 128);

    /**
     * 触发冷却时间（tick）
     * <p>默认 1800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "cooldown",
                    1800, 20, 144000);

    /**
     * 按目标当前生命计算的雷击伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_health_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 按目标最大生命计算的雷击伤害比例
     * <p>默认 0.005，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MAX_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "damage_max_health_ratio",
                    0.005D, 0.0D, 1.0D);

    /**
     * 雷击的击退强度
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.2D, 0.0D, 5.0D);

    /**
     * 高倍率档的伤害放大倍数
     * <p>默认 4，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_HIGH =
            EnchantmentValues.define(VALUE_ID, "magnification_high",
                    4, 1, 100);

    /**
     * 中倍率档的伤害放大倍数
     * <p>默认 2，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle MAGNIFICATION_MID =
            EnchantmentValues.define(VALUE_ID, "magnification_mid",
                    2, 1, 100);

    /**
     * 单次触发最大命中目标数。
     * <p>
     * 每个目标会创建一个独立的周期任务并持续广播特效 / 音效包，
     * 故必须封顶。取 {@value} 的理由见类注释「v3.1 新增：命中目标数硬上限」小节：
     * 常规战斗远达不到该值（不影响正常体验），密集场景下则守住并发任务数、
     * 网络包峰值与客户端红闪特效的存活名额。
     * </p>
     * <p>
     * <b>请勿放宽。</b>若确需更大范围的压制感，应调整 {@code level * 15} 的
     * 单目标落雷次数上限，而不是放开目标数——前者只增加时长，后者会线性放大
     * 并发任务与瞬时包量。
     * </p>
     */
    public EnchantmentAncientDragonLightning() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
