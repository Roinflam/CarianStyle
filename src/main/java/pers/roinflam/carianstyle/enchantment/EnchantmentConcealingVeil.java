package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 *
 * 隐匿面纱附魔
 * <p>
 * 护甲附魔，潜行时获得隐身效果
 * 攻击或受到攻击后3秒内无法隐身（战斗冷却）
 * </p>
 *
 * <h3>v2.1：两个战斗冷却常量改为 public（行为零变化）</h3>
 * <p>
 * {@code CarianStyleConditionDisplay} 需要读这条冷却来显示 HUD 倒计时。
 * 冷却记录在 {@link EnchantmentDataManager} 里是<b>按字符串键存的</b>，
 * 读取方必须拿到与写入方完全相同的键。
 * </p>
 * <p>
 * 与其在 HUD 那边复制一份 {@code "concealing_veil_battle"} 字面量，
 * 不如把这里的常量公开——<b>复制字面量的问题在于它不会跟着改</b>：
 * 哪天这里改了键名而那边没跟上，HUD 会安静地永远显示「无冷却」，
 * 既不报错也不会被测试发现。改成 public 之后，键名只有一处定义，
 * 改动会由编译器强制同步。
 * </p>
 * <p>
 * <b>本次只改了这两个字段的可见性修饰符，其余逻辑一行未动。</b>
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentConcealingVeil extends EnchantmentBase {
    private static final String VALUE_ID = "concealing_veil";
    public static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";
    public static final EnchantmentValues.Handle BATTLE_DURATION = EnchantmentValues.defineShared(VALUE_ID, "battle_duration", 60, 1, 12000);
    private static final EnchantmentValues.Handle STEALTH_REFRESH_TICKS = EnchantmentValues.define(VALUE_ID, "stealth_refresh_ticks", 2, 1, 100);
    protected void onAttackLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onHurtAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
}

