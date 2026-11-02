package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>卡利亚报复附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "carian_retaliation",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND},
        conflictsWith = {
                EnchantmentScholarShield.class,
                EnchantmentImmutableShield.class
        }
)
public class EnchantmentCarianRetaliation extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "carian_retaliation";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 反击辉剑的数量（发射方位自动均分整圆）
     * <p>默认 3，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle BLADE_COUNT =
            EnchantmentValues.define(VALUE_ID, "blade_count",
                    3, 1, 32);

    /**
     * 第一把剑的发射延迟（tick）
     * <p>默认 40，允许范围 0 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle BASE_DELAY =
            EnchantmentValues.define(VALUE_ID, "base_delay",
                    40, 0, 600);

    /**
     * 相邻两把剑的发射间隔（tick）
     * <p>默认 5，允许范围 0 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DELAY_STEP =
            EnchantmentValues.define(VALUE_ID, "delay_step",
                    5, 0, 120);

    /**
     * 每级的辉剑伤害倍率
     * <p>默认 0.2，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.2D, 0.0D, 5.0D);

    /**
     * 辉剑的追踪强度
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TRACKING_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "tracking_strength",
                    0.15D, 0.0D, 1.0D);

    /** 剑环半径（格），与原实现一致 */
    private static final double RING_RADIUS = 1.5;

    /** 剑环离持盾者脚底的高度（格），与原实现一致 */
    private static final double RING_HEIGHT = 0.5;

    public EnchantmentCarianRetaliation() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"),
                new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class)) &&
                !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentImmutableShield.class));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
