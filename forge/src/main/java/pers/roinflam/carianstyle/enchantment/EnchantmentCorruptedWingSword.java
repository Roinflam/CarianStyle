package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 腐败翼剑附魔
 * <p>
 * 武器附魔，连击系统
 * 每次攻击增加连击数（最多20）
 * 伤害加成 = 原伤害 × (连击数/4) × 3% × 等级
 * 15秒后连击数开始逐个衰减
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "corrupted_wing_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentCorruptedWingSword extends EnchantmentBase {

    private static final String COMBO_COUNTER_KEY = "corrupted_wing_sword_combo";
    private static final int MAX_COMBO = 20;
    private static final int DECAY_DELAY = 300; // 15秒 (300 ticks)

    public EnchantmentCorruptedWingSword() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        int currentCombo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());

        if (currentCombo < MAX_COMBO) {
            int newCombo = EnchantmentDataManager.incrementCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());

            new SynchronizationTask(DECAY_DELAY) {
                @Override
                public void run() {
                    int combo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());
                    if (combo > 1) {
                        EnchantmentDataManager.setCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID(), combo - 1);
                    } else {
                        EnchantmentDataManager.resetCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());
                    }
                }
            }.start();

            float damageBonus = ctx.getDamage() * (newCombo / 4.0f) * 0.03f * level;
            ctx.addDamage(damageBonus);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}