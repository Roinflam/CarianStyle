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
 * <p>隐匿面纱附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "concealing_veil",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentConcealingVeil extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "concealing_veil";

    /**
     * 战斗冷却在 {@link EnchantmentDataManager} 中的键。
     * <p><b>v2.1 由 private 改为 public</b>：HUD 需要用同一个键读取剩余时间，
     * 公开出去比在两处各写一遍字面量安全（详见类注释）。</p>
     */
    public static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";

    /**
     * 进入战斗状态后的计时时长（tick，默认 60 = 3 秒）。
     * <p>
     * 用 {@link EnchantmentValues#defineShared} 而非 {@code define}：本值同时被客户端的
     * {@code CarianStyleConditionDisplay} 用来画倒计时进度条。配置文件是各端各一份的，
     * 若不下发，服主改了时长之后玩家看到的进度条会与实际状态对不上——
     * 条走完了人还在战斗中，或者反过来。详见 {@link EnchantmentValues} 类注释。
     * </p>
     */
    public static final EnchantmentValues.Handle BATTLE_DURATION =
            EnchantmentValues.defineShared(VALUE_ID, "battle_duration", 60, 1, 12000);

    /**
     * 潜行状态每次续期的时长（tick）。
     * <p>这是个实现细节：每 tick 续 2 tick，靠不断续期维持状态。
     * 不建议改，调大只会让脱离战斗后的潜行多拖几 tick。</p>
     */
    private static final EnchantmentValues.Handle STEALTH_REFRESH_TICKS =
            EnchantmentValues.define(VALUE_ID, "stealth_refresh_ticks", 2, 1, 100);

    public EnchantmentConcealingVeil() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
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
