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
 * <p>癫火蔓延附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "epilepsy_spread";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 16，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    16, 1, 64);

    /**
     * 单次触发最大命中目标数
     * <p>默认 24，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    24, 1, 200);

    /**
     * 濒死标记的过期时长（tick）
     * <p>默认 72000，允许范围 20 ~ 432000。</p>
     */
    private static final EnchantmentValues.Handle PENDING_DEATH_EXPIRY =
            EnchantmentValues.define(VALUE_ID, "pending_death_expiry",
                    72000, 20, 432000);

    /**
     * 濒死判定的延迟（tick）
     * <p>默认 20，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle PENDING_DEATH_DELAY =
            EnchantmentValues.define(VALUE_ID, "pending_death_delay",
                    20, 1, 1200);

    /**
     * 触发所需的剩余生命比例阈值
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 触发后自身保留的生命比例
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REVIVE_HEALTH_RATIO =
            EnchantmentValues.define(VALUE_ID, "revive_health_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 触发冷却时间（tick）
     * <p>默认 1800，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "cooldown",
                    1800, 20, 144000);

    /**
     * 每级增加的搜索半径（格，受上限截断）
     * <p>默认 4，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle RADIUS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "radius_per_level",
                    4, 1, 32);

    /**
     * 每级的击退强度系数
     * <p>默认 0.7，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "knockback_per_level",
                    0.7D, 0.0D, 5.0D);

    /**
     * 自身持续伤害的最大生命占比总量
     * <p>默认 0.3，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DOT_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_dot_ratio",
                    0.3D, 0.0D, 1.0D);

    /**
     * 每级施加给目标的持续伤害占比总量
     * <p>默认 0.3，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle TARGET_DOT_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "target_dot_per_level",
                    0.3D, 0.0D, 2.0D);

    /**
     * 「待补刀」标记键。
     * <p>演出结束时若持有者已离线（补刀空转），登记此标记；
     * 玩家重新登录后由 {@link ServerEventHandler} 补上死亡，详见类注释「v3.1 修复」小节。</p>
     */
    private static final String PENDING_DEATH_KEY = "epilepsy_spread_pending_death";

    /**
     * 重登补刀的延迟（tick）。
     * <p>1 秒。等客户端完成进入世界的初始化再执行死亡，避免死亡画面在加载过程中弹出。</p>
     */
    public EnchantmentEpilepsySpread() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
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
