package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 隐匿面纱附魔
 *
 * 护甲附魔，潜行时获得隐身效果
 * 攻击后3秒内无法隐身（战斗冷却）
 */
@AutoRegisterEnchantment(
        id = "concealing_veil",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        conflictsWith = {
                // 与原版保护冲突
        }
)
@Mod.EventBusSubscriber
public class EnchantmentConcealingVeil extends EnchantmentBase {

    private static final String BATTLE_COOLDOWN_KEY = "concealing_veil_battle";
    private static final int BATTLE_DURATION = 60; // 3秒 (60 ticks)

    public EnchantmentConcealingVeil() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 攻击时标记战斗状态
     * 使用基类模板方法
     */
    @Override
    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
        // 玩家攻击时设置战斗冷却
        if (ctx.isHolderPlayer()) {
            EnchantmentDataManager.setCooldown(
                    BATTLE_COOLDOWN_KEY,
                    ctx.getHolder().getUniqueID(),
                    BATTLE_DURATION
            );
        }
    }

    /**
     * 每tick检查潜行状态
     * 潜行且不在战斗中时给予隐身效果
     */
    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        // 必须正在潜行
        if (!ctx.getHolderAsPlayer().isSneaking()) {
            return;
        }

        // 检查是否在战斗冷却中
        if (EnchantmentDataManager.isOnCooldown(BATTLE_COOLDOWN_KEY, ctx.getHolder().getUniqueID())) {
            return;
        }

        // 施加隐身效果（持续2 tick，需要持续刷新）
        ctx.addPotionToHolder(CarianStylePotion.STEALTH, 2, 0);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}