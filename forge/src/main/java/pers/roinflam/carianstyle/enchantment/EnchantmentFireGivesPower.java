package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 火焰赋予力量附魔
 * <p>
 * 武器附魔，火焰增益效果
 * 攻击时：
 * - 如果自己着火：伤害增加 7.5% × 等级，且续燃10秒（如果燃烧时间<200tick）
 * - 如果自己没着火：点燃自己10秒（创造模式免疫）
 * 受击时：
 * - 如果自己着火且伤害是物理伤害：减伤 3.75% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "fire_gives_power",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentFireGivesPower extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.fire_gives_power.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "fire_gives_power";

    /**
     * 燃烧时每级的额外伤害倍率
     * <p>默认 0.075，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.075D, 0.0D, 5.0D);

    /**
     * 对燃烧目标每级的伤害削减倍率
     * <p>默认 0.0375，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.0375D, 0.0D, 1.0D);

    /**
     * 自燃续期的秒数
     * <p>默认 10，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle IGNITE_SECONDS =
            EnchantmentValues.define(VALUE_ID, "ignite_seconds",
                    10, 1, 300);

    public EnchantmentFireGivesPower() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        if (attacker.getRemainingFireTicks() > 0) {
            if (attacker.getRemainingFireTicks() < 200) {
                attacker.setSecondsOnFire(IGNITE_SECONDS.getInt());
            }
            float bonusDamage = ctx.getDamage() * level * (float) BONUS_DAMAGE_PER_LEVEL.get();
            ctx.addDamage(bonusDamage);
        } else {
            if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
                attacker.setSecondsOnFire(IGNITE_SECONDS.getInt());
            }
        }
    }

    @Override
    protected void onDamageAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        if (victim.getRemainingFireTicks() <= 0) {
            return;
        }

        if (ctx.canHarmInCreative() || ctx.isMagicDamage()) {
            return;
        }

        float reduction = ctx.getDamage() * level * (float) REDUCTION_PER_LEVEL.get();
        ctx.reduceDamage(reduction);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
