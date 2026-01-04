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

    /**
     * 攻击时累计计数，每4次攻击触发治疗
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        // 获取当前攻击计数
        int currentCount = EnchantmentDataManager.getCounter(ATTACK_COUNTER, attacker.getUniqueID());

        if (currentCount == 3) {
            // 第4次攻击，触发治疗并重置计数
            EnchantmentDataManager.resetCounter(ATTACK_COUNTER, attacker.getUniqueID());

            // 治疗量 = 最大生命值 × (3% + (等级-1) × 1%)
            float healAmount = attacker.getMaxHealth() * 0.03f + attacker.getMaxHealth() * (level - 1) * 0.01f;
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