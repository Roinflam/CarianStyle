package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>吃屎附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "eat_shit", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND})
public class EnchantmentEatShit extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "eat_shit";

    /**
     * 每级施加给目标的效果时长（tick）
     * <p>默认 80，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_ticks_per_level",
                    80, 1, 1200);

    /**
     * 每级施加给自身的混乱时长（tick）
     * <p>默认 30，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle SELF_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "self_ticks_per_level",
                    30, 1, 1200);

    /**
     * 触发时的伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "damage_multiplier",
                    0.25D, 0.0D, 5.0D);

    /**
     * 治疗削减 debuff 在 {@link EnchantmentDataManager} 中的键。
     * <p><b>v2.3 由 private 改为 public</b>：HUD 需要用同一个键读取剩余时间。
     * 注意这条记录是打在<b>受击者</b>身上的，与受击者自己的装备无关（详见类注释）。</p>
     */
    public static final String DEBUFF_KEY = "eat_shit_debuff";

    public EnchantmentEatShit() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 2) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
