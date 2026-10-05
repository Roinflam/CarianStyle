package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 催眠烟雾附魔
 * <p>
 * 武器附魔，概率使目标入睡
 * 攻击时：
 * - 2% × 等级的概率触发
 * - 延迟5tick后施加睡眠效果（持续 = 等级 × 3秒，效果等级 = 附魔等级 - 1）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "hypnotic_smoke",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentHypnoticSmoke extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.hypnotic_smoke.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "hypnotic_smoke";

    /**
     * 每级的触发概率（百分比）
     * <p>默认 2.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.0D, 0.0D, 100.0D);

    /**
     * 触发到施加效果的延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle APPLY_DELAY =
            EnchantmentValues.define(VALUE_ID, "apply_delay",
                    5, 0, 200);

    /**
     * 每级的催眠持续秒数
     * <p>默认 3，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    3, 1, 120);

    public EnchantmentHypnoticSmoke() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 连点等于多掷几次骰子，刷出睡眠后再补一记满蓄力就能吃到加倍伤害，要求满蓄力
        if (!isFullyCharged(ctx.getHolder())) {
            return;
        }

        if (!RandomUtil.percentageChance(level * CHANCE_PER_LEVEL.get())) {
            return;
        }

        new SynchronizationTask(APPLY_DELAY.getInt()) {
            @Override
            public void run() {
                victim.addEffect(new MobEffectInstance(
                        CarianStylePotion.SLEEP.get(),
                        level * DURATION_SECONDS_PER_LEVEL.getInt() * 20,
                        level - 1
                ));
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class));
    }
}
