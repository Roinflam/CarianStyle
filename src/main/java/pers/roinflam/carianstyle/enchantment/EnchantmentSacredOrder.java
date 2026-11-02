package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>神圣秩序附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentSacredOrder extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sacred_order";

    /**
     * 伤害吸收上限相对最大生命的倍数
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "absorption_cap_ratio",
                    3.0D, 0.0D, 20.0D);

    /**
     * 每次击杀获得的伤害吸收占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_PER_KILL =
            EnchantmentValues.define(VALUE_ID, "absorption_per_kill",
                    0.1D, 0.0D, 2.0D);

    /**
     * 自身有吸收护盾时受到伤害的倍率
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SHIELDED_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "shielded_damage_multiplier",
                    0.75D, 0.0D, 1.0D);

    /**
     * 按目标剩余吸收量反弹给攻击者的伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_RATIO =
            EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 攻击带吸收护盾的目标时的伤害倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle VS_SHIELDED_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "vs_shielded_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 这条命已经领过初始吸收盾的标记，存在实体持久数据里。
     * <p>玩家死亡重生是新实体、标记不带过去（不放进 PERSISTED_NBT_TAG），重生后能再领一次；
     * 切维度、重新登录时标记还在。</p>
     */
    private static final String INITIAL_SHIELD_GIVEN_KEY = "carianstyle_sacred_order_shield_given";

    public EnchantmentSacredOrder() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
