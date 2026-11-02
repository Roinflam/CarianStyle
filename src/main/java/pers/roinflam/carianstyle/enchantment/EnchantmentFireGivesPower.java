package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import javax.annotation.Nonnull;
import java.lang.reflect.Field;

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
 * <p>
 * 1.12.2 版：剩余燃烧时间读 {@code Entity} 的私有字段 {@code fire}（1.20.1 是 {@code getRemainingFireTicks()}）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "fire_gives_power",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentFireGivesPower extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.fire_gives_power.desc，
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

    /** 1.12.2 版：{@code Entity.fire} 是私有字段，反射读剩余燃烧 tick */
    private static final Field FIRE_FIELD = Reflect.fieldOrNull(Entity.class, "fire", "field_190534_ay");

    public EnchantmentFireGivesPower() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        if (remainingFireTicks(attacker) > 0) {
            if (remainingFireTicks(attacker) < 200) {
                attacker.setFire(IGNITE_SECONDS.getInt());
            }
            float bonusDamage = ctx.getDamage() * level * (float) BONUS_DAMAGE_PER_LEVEL.get();
            ctx.addDamage(bonusDamage);
        } else {
            if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                attacker.setFire(IGNITE_SECONDS.getInt());
            }
        }
    }

    @Override
    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        if (remainingFireTicks(victim) <= 0) {
            return;
        }

        if (ctx.canHarmInCreative() || ctx.isMagicDamage()) {
            return;
        }

        float reduction = ctx.getDamage() * level * (float) REDUCTION_PER_LEVEL.get();
        ctx.reduceDamage(reduction);
    }

    /**
     * 剩余燃烧 tick（1.20.1 的 {@code getRemainingFireTicks()}）
     *
     * @param entity 实体
     * @return 剩余燃烧 tick；字段读不到时退回 {@code isBurning()}
     */
    private static int remainingFireTicks(@Nonnull Entity entity) {
        if (FIRE_FIELD == null) {
            return entity.isBurning() ? 1 : 0;
        }
        try {
            return FIRE_FIELD.getInt(entity);
        } catch (IllegalAccessException e) {
            return entity.isBurning() ? 1 : 0;
        }
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
