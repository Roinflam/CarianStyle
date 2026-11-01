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
    /** 连击层数上限 */
    private static final int MAX_COMBO = 20;
    /** 每层多久后衰减（tick） */
    private static final int DECAY_DELAY = 300;
    /** 层数折算除数（每 4 层算一档） */
    private static final float COMBO_DIVISOR = 4.0F;
    /** 每档每级增伤 */
    private static final float DAMAGE_PER_LEVEL = 0.03F;

    public EnchantmentCorruptedWingSword() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 满蓄力攻击叠 1 层连击并按层数增伤
     * <p>
     * 层数打满后不再叠、也不再追加衰减任务，但增伤按满层算。
     * 以前增伤只写在「没满」的分支里，打满 20 层后加成反而消失，要等衰减掉一层才回来。
     * </p>
     */
    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        int currentCombo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());

        int newCombo;
        if (currentCombo < MAX_COMBO) {
            newCombo = EnchantmentDataManager.incrementCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUniqueID());

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
        } else {
            newCombo = MAX_COMBO;
        }

        float damageBonus = ctx.getDamage() * (newCombo / COMBO_DIVISOR) * DAMAGE_PER_LEVEL * level;
        ctx.addDamage(damageBonus);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }
}