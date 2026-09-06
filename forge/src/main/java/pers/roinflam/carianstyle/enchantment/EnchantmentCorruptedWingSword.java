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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
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

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.corrupted_wing_sword.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "corrupted_wing_sword";

    /**
     * 连击层数上限
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_COMBO =
            EnchantmentValues.define(VALUE_ID, "max_combo",
                    20, 1, 200);

    /**
     * 连击开始衰减前的延迟（tick）
     * <p>默认 300，允许范围 20 ~ 12000。</p>
     */
    private static final EnchantmentValues.Handle DECAY_DELAY =
            EnchantmentValues.define(VALUE_ID, "decay_delay",
                    300, 20, 12000);

    /**
     * 连击层数的换算除数（越小同层数增伤越高）
     * <p>默认 4.0，允许范围 0.5 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle COMBO_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "combo_divisor",
                    4.0D, 0.5D, 100.0D);

    /**
     * 每级每档连击提供的额外伤害倍率
     * <p>默认 0.03，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.03D, 0.0D, 2.0D);


    private static final String COMBO_COUNTER_KEY = "corrupted_wing_sword_combo";

    public EnchantmentCorruptedWingSword() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        int currentCombo = EnchantmentDataManager.getCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());

        if (currentCombo < MAX_COMBO.getInt()) {
            int newCombo = EnchantmentDataManager.incrementCounter(COMBO_COUNTER_KEY, ctx.getHolder().getUUID());

            new SynchronizationTask(DECAY_DELAY.getInt()) {
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

            float damageBonus = ctx.getDamage() * (float) (newCombo / COMBO_DIVISOR.get())
                * (float) DAMAGE_PER_LEVEL.get() * level;
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
