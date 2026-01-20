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
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "concealing_veil",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentConcealingVeil extends EnchantmentBase {

    private static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";
    private static final int BATTLE_DURATION = 60; // 3秒 (60 ticks)

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