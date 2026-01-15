// 文件：EnchantmentMagicScorpionCharm.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/EnchantmentMagicScorpionCharm.java
package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;

/**
 * 魔力蝎符附魔
 *
 * 效果：
 * - 造成的魔法伤害增加 10% × 等级
 * - 造成魔法伤害时恢复自身已损失生命值 1% × 等级
 * - 受到物理伤害时伤害增加 10% × 等级
 * - 最大等级：5
 */
@AutoRegisterEnchantment(
        id = "magic_scorpion_charm",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentMagicScorpionCharm extends EnchantmentBase {

    /**
     * 构造函数
     */
    public EnchantmentMagicScorpionCharm() {
        // 护甲附魔，附魔在胸甲上
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 造成魔法伤害时增伤并恢复生命（LOWEST优先级，最终伤害）
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        DamageSource source = ctx.getDamageSource();

        // 必须是魔法伤害
        if (!isMagicDamage(source)) {
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

    /**
     * 受到物理伤害时增加伤害（HIGHEST优先级，优先处理）
     */
    @Override
    protected void onHurtAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
        DamageSource source = ctx.getDamageSource();

        // 必须是物理伤害
        if (!isPhysicalDamage(source)) {
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

    /**
     * 判断是否为魔法伤害
     *
     * @param source 伤害源
     * @return 是否为魔法伤害
     */
    private boolean isMagicDamage(@Nonnull DamageSource source) {
        // 魔法伤害包括：
        // 1. 标记为魔法的伤害
        // 2. 投射物伤害（箭、火球等）
        // 3. 间接伤害（非直接攻击）
        return source.isMagicDamage() ||
                source.isProjectile() ||
                (source.getImmediateSource() != source.getTrueSource());
    }

    /**
     * 判断是否为物理伤害
     *
     * @param source 伤害源
     * @return 是否为物理伤害
     */
    private boolean isPhysicalDamage(@Nonnull DamageSource source) {
        // 物理伤害的特征：
        // 1. 不是魔法伤害
        // 2. 不是投射物
        // 3. 直接来源是生物（近战攻击）
        // 4. 排除环境伤害（火焰、摔落等）
        if (source.isMagicDamage() || source.isProjectile()) {
            return false;
        }

        // 排除环境伤害
        if (source.isFireDamage() || source.getDamageType().equals("fall") ||
                source.getDamageType().equals("drown") || source.getDamageType().equals("starve")) {
            return false;
        }

        // 必须有直接来源且是生物
        return source.getImmediateSource() instanceof EntityLivingBase;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        // UNCOMMON 的默认公式：5 + (level - 1) * 10
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}