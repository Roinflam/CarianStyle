package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
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

/**
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
@AutoRegisterEnchantment(
        id = "concealing_veil",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentConcealingVeil extends EnchantmentBase {

    /**
     * 战斗冷却在 {@link EnchantmentDataManager} 中的键。
     * <p><b>v2.1 由 private 改为 public</b>：HUD 需要用同一个键读取剩余时间，
     * 公开出去比在两处各写一遍字面量安全（详见类注释）。</p>
     */
    public static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";

    /**
     * 战斗冷却时长（tick）：3 秒。
     * <p><b>v2.1 由 private 改为 public</b>：HUD 用它作为充能进度条的总长度。</p>
     */
    public static final int BATTLE_DURATION = 60; // 3秒 (60 ticks)

    public EnchantmentConcealingVeil() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 攻击时标记战斗状态
     */
    @Override
    protected void onAttackLowest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer()) {
            EnchantmentDataManager.setCooldown(
                    BATTLE_COOLDOWN_KEY,
                    ctx.getHolder().getUUID(),
                    BATTLE_DURATION
            );
        }
    }

    /**
     * 受到攻击时也标记战斗状态（修复受击后立刻隐身的bug）
     */
    @Override
    protected void onHurtAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer()) {
            EnchantmentDataManager.setCooldown(
                    BATTLE_COOLDOWN_KEY,
                    ctx.getHolder().getUUID(),
                    BATTLE_DURATION
            );
        }
    }

    /**
     * 每tick检查潜行状态
     */
    @Override
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
        // 检查是否在潜行
        if (!ctx.getHolderAsPlayer().isShiftKeyDown()) {
            return;
        }

        // 检查是否在战斗冷却中
        if (EnchantmentDataManager.isOnCooldown(BATTLE_COOLDOWN_KEY, ctx.getHolder().getUUID())) {
            return;
        }

        // 应用隐身效果（持续2 tick）
        DynamicAttributeManager.apply(ctx.getHolder(),
                DynamicAttributes.STEALTH.createInstance(2, 0));
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
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
