package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(B2a)
 * <p>艾奥尼亚附魔 - 优化: LivingTickEvent -> PlayerTickEvent @version 2.1：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "aeonia", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class})
public class EnchantmentAeonia extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "aeonia";

    /**
     * 命中时回复的最大生命占比
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.1D, 0.0D, 2.0D);

    /**
     * 自身腐败续期的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次续期施加的自身猩红腐败时长（tick，需略大于结算间隔）
     * <p>默认 21，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle SELF_ROT_DURATION =
            EnchantmentValues.define(VALUE_ID, "self_rot_duration",
                    21, 1, 600);

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentAeonia() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
