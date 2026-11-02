package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;

/**
 * PORT-STUB(E2)
 * <p>满月附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "full_moon", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnumEnchantmentType.ARMOR_CHEST, slots = {EntityEquipmentSlot.CHEST}, conflictsWith = {EnchantmentHealingByFire.class, EnchantmentShelterOfFire.class})
public class EnchantmentFullMoon extends EnchantmentBase {

    private static final String FULL_MOON_STATE_KEY = "full_moon_state";

    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /** 不带暗月时的回血持续时间（游戏刻）= 10 秒 */
    private static final int HEAL_DURATION_TICKS = 200;

    /** 装备暗月时的回血持续时间（游戏刻）= 20 秒 */
    private static final int HEAL_DURATION_TICKS_DARK_MOON = 400;

    public EnchantmentFullMoon() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (ench instanceof EnchantmentTimeReversal) return false;
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int l) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
