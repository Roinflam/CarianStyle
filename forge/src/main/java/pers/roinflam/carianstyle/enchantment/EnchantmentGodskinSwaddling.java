package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;

/**
 * 神皮襁褓附魔
 * <p>
 * 武器附魔，连续攻击回血
 * 每攻击4次触发一次治疗：
 * - 治疗量 = 最大生命值 × (3% + (等级 - 1) × 1%)
 * - 等级1: 3%，等级2: 4%，等级3: 5%...
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "godskin_swaddling",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentGodskinSwaddling extends EnchantmentBase {

    private static final String ATTACK_COUNTER = "godskin_swaddling_attack";
    private static final int COUNTER_EXPIRY = 6000;

    public EnchantmentGodskinSwaddling() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        int currentCount = EnchantmentDataManager.getCounter(ATTACK_COUNTER, attacker.getUUID());

        if (currentCount == 3) {
            EnchantmentDataManager.resetCounter(ATTACK_COUNTER, attacker.getUUID());

            float healAmount = attacker.getMaxHealth() * 0.03f + attacker.getMaxHealth() * (level - 1) * 0.01f;
            attacker.heal(healAmount);
        } else {
            EnchantmentDataManager.incrementCounter(ATTACK_COUNTER, attacker.getUUID(), COUNTER_EXPIRY);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}