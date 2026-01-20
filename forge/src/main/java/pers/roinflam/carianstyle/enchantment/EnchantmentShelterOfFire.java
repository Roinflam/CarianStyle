package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.config.ConfigLoader;

/**
 * 火焰庇护附魔
 * <p>
 * 护甲附魔，着火时减伤并回血
 * 着火时：
 * - 受到伤害减少 2% × 等级（50级时完全免疫）
 * - 每秒恢复 0.1% × 等级 最大生命值
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "shelter_of_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentShelterOfFire extends EnchantmentBase {

    public EnchantmentShelterOfFire() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 着火时减伤（受害者视角，低优先级）
     */
    @Override
    protected void onDamageAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        // 必须处于着火状态
        if (victim.getRemainingFireTicks() <= 0) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 计算减伤比例：2% × 等级
        float damageReduction = effectiveLevel * 0.02f;

        // 如果减伤 >= 100%，则完全免疫
        if (damageReduction >= 1.0f) {
            ctx.cancelEvent();
        } else {
            // 否则按比例减伤
            ctx.multiplyDamage(1.0f - damageReduction);
        }
    }

    /**
     * 着火时每tick回血（玩家Tick事件）
     */
    @Override
    protected void onPlayerTick(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity entity = ctx.getHolder();

        // 必须处于着火状态
        if (entity.getRemainingFireTicks() <= 0) {
            return;
        }

        // 必须存活
        if (!entity.isAlive()) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 每秒恢复 0.1% × 等级 最大生命值
        // 每tick恢复 = (maxHealth × 0.001 × level) / 20
        float healAmount = entity.getMaxHealth() * effectiveLevel * 0.001f / 20.0f;
        entity.heal(healAmount);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
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