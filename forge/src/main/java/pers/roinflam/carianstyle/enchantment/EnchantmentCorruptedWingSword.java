package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 腐败翼剑附魔
 *
 * 武器附魔，连击系统
 * 每次攻击增加连击数（最多20）
 * 伤害加成 = 原伤害 × (连击数/4) × 3% × 等级
 * 15秒后连击数开始逐个衰减
 */
@AutoRegisterEnchantment(
        id = "corrupted_wing_sword",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentCorruptedWingSword extends EnchantmentBase {

    private static final String COMBO_COUNTER_KEY = "corrupted_wing_sword_combo";
    private static final int MAX_COMBO = 20;
    private static final int DECAY_DELAY = 300; // 15秒 (300 ticks)

    public EnchantmentCorruptedWingSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时累积连击并增加伤害
     */
    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 检查是否为玩家且刚挥动武器
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        // 获取当前连击数
        int currentCombo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());

        // 连击数未达到上限时增加
        if (currentCombo < MAX_COMBO) {
            // 递增连击数
            int newCombo = EnchantmentDataManager.incrementCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());

            // 启动衰减任务
            new SynchronizationTask(DECAY_DELAY) {
                @Override
                public void run() {
                    int combo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());
                    if (combo > 1) {
                        EnchantmentDataManager.setCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID(), combo - 1);
                    } else {
                        EnchantmentDataManager.resetCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());
                    }
                }
            }.start();

            // 计算并增加伤害
            float damageBonus = ctx.getDamage() * (newCombo / 4.0f) * 0.03f * level;
            ctx.addDamage(damageBonus);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }
}