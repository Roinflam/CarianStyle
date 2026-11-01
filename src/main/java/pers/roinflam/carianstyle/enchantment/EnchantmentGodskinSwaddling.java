package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;

import javax.annotation.Nonnull;

/**
 * 神皮襁褓附魔
 *
 * 武器附魔，连续攻击回血
 * 每攻击4次触发一次治疗：
 * - 治疗量 = 最大生命值 × (3% + (等级 - 1) × 1%)
 * - 等级1: 3%，等级2: 4%，等级3: 5%...
 * <p>
 * 只统计满蓄力的命中，以前连点、半蓄力也会计数。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "godskin_swaddling",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentGodskinSwaddling extends EnchantmentBase {

    /**
     * 攻击计数器ID
     */
    private static final String ATTACK_COUNTER = "godskin_swaddling_attack";

    /**
     * 计数器过期时间（6000tick = 5分钟，与原代码清理周期一致）
     */
    private static final int COUNTER_EXPIRY = 6000;

    public EnchantmentGodskinSwaddling() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /** 计数到这个数之后的下一次命中触发治疗 */
    private static final int TRIGGER_COUNT = 3;
    /** 基础治疗量占最大生命值的比例 */
    private static final float BASE_HEAL_RATIO = 0.03F;
    /** 每超过一级增加的治疗比例 */
    private static final float HEAL_PER_EXTRA_LEVEL = 0.01F;

    /**
     * 满蓄力命中时累计计数，每 4 次触发治疗
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        // 蓄力不满的攻击既不计数也不回血
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 获取当前攻击计数
        int currentCount = EnchantmentDataManager.getCounter(ATTACK_COUNTER, attacker.getUniqueID());

        if (currentCount == TRIGGER_COUNT) {
            // 第4次攻击，触发治疗并重置计数
            EnchantmentDataManager.resetCounter(ATTACK_COUNTER, attacker.getUniqueID());

            // 治疗量 = 最大生命值 × (3% + (等级-1) × 1%)
            float healAmount = attacker.getMaxHealth() * BASE_HEAL_RATIO + attacker.getMaxHealth() * (level - 1) * HEAL_PER_EXTRA_LEVEL;
            attacker.heal(healAmount);
        } else {
            // 增加计数（带过期时间）
            EnchantmentDataManager.incrementCounter(ATTACK_COUNTER, attacker.getUniqueID(), COUNTER_EXPIRY);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}