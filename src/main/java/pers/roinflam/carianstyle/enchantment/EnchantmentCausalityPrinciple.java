package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.AoeHelper;

/**
 * PORT-STUB(E3)
 * <p>因果律附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "causality_principle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentCausalityPrinciple extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "causality_principle";

    /**
     * AOE 反击搜索半径上限（格）
     * <p>默认 10，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10, 1, 64);

    /**
     * 单次 AOE 反击最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 触发 AOE 反击所需的受击累积次数
     * <p>默认 5，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_COUNT =
            EnchantmentValues.define(VALUE_ID, "trigger_count",
                    5, 1, 100);

    /**
     * AOE 反击触发冷却（tick）
     * <p>默认 20，允许范围 0 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "trigger_cooldown",
                    20, 0, 1200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的反击伤害倍率（基于本次受到的伤害）
     * <p>默认 0.75，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.75D, 0.0D, 10.0D);

    /** 受击累积计数器键 */
    private static final String COUNTER_KEY = "causality_principle";

    /** AOE 反击触发冷却键 */
    private static final String COOLDOWN_KEY = "causality_principle_cooldown";

    /**
     * 本附魔专属的线程级重入保护。
     * <p>
     * AOE 反击对周围实体造成的二次伤害会再次进入 {@link #onLivingDamage}，
     * 此守卫在方法入口处直接拦截，防止因果律自身递归触发，
     * 进而避免与其它模组的反伤、加属性机制形成伤害事件级联导致服务端崩溃。
     * </p>
     * <p>
     * <b>每个附魔必须持有自己独立的一份</b>，不同附魔之间互不影响
     * （因果律的守卫不应阻止学者盾的反伤）。
     * </p>
     */
    private static final AoeHelper.ReentrancyGuard AOE_GUARD = new AoeHelper.ReentrancyGuard();

    public EnchantmentCausalityPrinciple() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
