package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>诺克斯之月附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "moon_of_noxtura", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.ARMOR, slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class, EnchantmentPreciseLightning.class, EnchantmentAncientDragonLightning.class}, forceTreasure = true)
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "moon_of_noxtura";

    /**
     * 仇恨转移的结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle TICK_INTERVAL =
            EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);

    /**
     * 每次结算的触发概率（百分比）
     * <p>默认 2.5，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    2.5D, 0.0D, 100.0D);

    /**
     * 仇恨转移的搜索半径（格）
     * <p>默认 32，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "search_radius",
                    32, 1, 64);

    public EnchantmentMoonOfNoxtura() { super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}); }

    @Override public int getMinCost(int l) { return (int)(35 * ConfigLoader.enchantingDifficulty); }

    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
