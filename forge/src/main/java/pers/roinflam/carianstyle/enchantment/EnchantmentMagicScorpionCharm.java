package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 魔力蝎符附魔
 * <p>
 * 效果：
 * - 造成的魔法伤害增加 10% × 等级
 * - 造成魔法伤害时恢复自身已损失生命值 1% × 等级
 * - 受到物理伤害时伤害增加 10% × 等级
 * - 最大等级：5
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "magic_scorpion_charm",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
public class EnchantmentMagicScorpionCharm extends EnchantmentBase {

    public EnchantmentMagicScorpionCharm() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        DamageSource source = ctx.getDamageSource();

        // 必须是魔法伤害
        if (!DamageSourceUtil.isMagicDamage(source)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 增加魔法伤害 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * 0.1f);

        // 恢复已损失生命值 1% × 等级
        float maxHealth = attacker.getMaxHealth();
        float currentHealth = attacker.getHealth();
        float lostHealth = maxHealth - currentHealth;

        if (lostHealth > 0) {
            float healAmount = lostHealth * effectiveLevel * 0.01f;
            attacker.heal(healAmount);
        }
    }

    @Override
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource source = ctx.getDamageSource();

        // 必须是物理伤害
        if (!DamageSourceUtil.isPhysicalDamage(source)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 受到的物理伤害增加 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * 0.1f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}