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
import java.util.Locale;

/**
 * 魔力蝎符附魔
 *
 * 效果：
 * - 造成的魔法伤害增加 10% × 等级
 * - 造成魔法伤害时恢复自身已损失生命值 1% × 等级
 * - 受到物理伤害时伤害增加 10% × 等级
 * - 最大等级：5
 * <p>
 * 魔法伤害 = 原版标记为魔法的伤害（药水、喷溅、荆棘等），或伤害类型名里带 magic 的；
 * 其余全部算物理伤害（近战、箭、火焰、摔落、爆炸……）。
 * 以前把所有投射物和间接伤害也当魔法，物理伤害又只算生物直接近战，增益和代价都偏了。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "magic_scorpion_charm",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentMagicScorpionCharm extends EnchantmentBase {

    /** 每级伤害倍率加成（魔法增伤、物理承伤共用） */
    private static final float DAMAGE_PER_LEVEL = 0.1F;
    /** 每级按已损失生命回复的比例 */
    private static final float HEAL_PER_LEVEL = 0.01F;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;

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
        int effectiveLevel = capLevel(level, LEVEL_CAP);

        // 增加魔法伤害 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * DAMAGE_PER_LEVEL);

        // 恢复已损失生命值 1% × 等级
        float maxHealth = attacker.getMaxHealth();
        float currentHealth = attacker.getHealth();
        float lostHealth = maxHealth - currentHealth;

        if (lostHealth > 0) {
            float healAmount = lostHealth * effectiveLevel * HEAL_PER_LEVEL;
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
        int effectiveLevel = capLevel(level, LEVEL_CAP);

        // 受到的物理伤害增加 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * DAMAGE_PER_LEVEL);
    }

    /**
     * 判断是否为魔法伤害：原版魔法标记，或伤害类型名里带 magic（别的模组自定义的魔法伤害）
     *
     * @param source 伤害源
     * @return 是否为魔法伤害
     */
    private static boolean isMagicDamage(@Nonnull DamageSource source) {
        return source.isMagicDamage()
                || source.getDamageType().toLowerCase(Locale.ROOT).contains("magic");
    }

    /**
     * 判断是否为物理伤害：魔法以外的一切伤害
     *
     * @param source 伤害源
     * @return 是否为物理伤害
     */
    private static boolean isPhysicalDamage(@Nonnull DamageSource source) {
        return !isMagicDamage(source);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        // UNCOMMON 的默认公式：5 + (level - 1) * 10
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}