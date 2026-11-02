package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>血的收藏附魔 - 修复: BloodSlash联动检查getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "blood_collection", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnumEnchantmentType.WEAPON, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentScarletCorruption.class, EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class, EnchantmentVicDragonThunder.class, EnchantmentDarkMoon.class})
public class EnchantmentBloodCollection extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "blood_collection";

    /**
     * 每级按已损失生命比例提供的额外伤害倍率
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.15D, 0.0D, 5.0D);

    /**
     * 基础治疗系数
     * <p>默认 0.02，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier",
                    0.02D, 0.0D, 1.0D);

    /**
     * 同时装备「鲜血斩击」时的治疗系数
     * <p>默认 0.04，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_MULTIPLIER_WITH_SLASH =
            EnchantmentValues.define(VALUE_ID, "heal_multiplier_with_slash",
                    0.04D, 0.0D, 1.0D);

    public EnchantmentBloodCollection() { super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND}); }

    @Override public int getMinCost(int l) { return (int)((25 + (l - 1) * 15) * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
