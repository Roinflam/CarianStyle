package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.mc112.util.EntityFireAccess;

/**
 * 火焰庇护附魔
 * <p>
 * 护甲附魔，着火时减伤并回血
 * 着火时：
 * - 受到伤害减少 2% × 等级（50级时完全免疫）
 * - 每秒恢复 0.1% × 等级 最大生命值
 * </p>
 * <p>
 * 1.12.2 版：剩余燃烧时间读 {@code Entity} 的私有字段 {@code fire}（1.20.1 是 {@code getRemainingFireTicks()}），
 * 存活时间是 {@code ticksExisted}（1.20.1 是 {@code tickCount}）。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "shelter_of_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET}
)
public class EnchantmentShelterOfFire extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.shelter_of_fire.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "shelter_of_fire";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的免疫火焰减伤比例
     * <p>默认 0.02，允许范围 0.0 ~ 0.2。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.02D, 0.0D, 0.2D);

    /**
     * 燃烧时的回血结算间隔（tick）
     * <p>默认 20，允许范围 1 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle HEAL_INTERVAL_TICKS =
            EnchantmentValues.define(VALUE_ID, "heal_interval_ticks",
                    20, 1, 600);

    /**
     * 每次结算每级回复的最大生命占比
     * <p>默认 0.001，允许范围 0.0 ~ 0.1。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.001D, 0.0D, 0.1D);

    public EnchantmentShelterOfFire() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 着火时减伤（受害者视角，低优先级）
     */
    @Override
    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        // 必须处于着火状态
        if (EntityFireAccess.getRemainingFireTicks(victim) <= 0) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        // 计算减伤比例：2% × 等级
        float damageReduction = effectiveLevel * (float) REDUCTION_PER_LEVEL.get();

        // 如果减伤 >= 100%，则完全免疫
        if (damageReduction >= 1.0f) {
            ctx.cancelEvent();
        } else {
            // 否则按比例减伤
            ctx.multiplyDamage(1.0f - damageReduction);
        }
    }

    /**
     * 着火时回血（玩家Tick事件）
     * 优化：每20tick（1秒）回血一次，避免每tick触发LivingHealEvent事件链
     */
    @Override
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase entity = ctx.getHolder();

        // 必须处于着火状态
        if (EntityFireAccess.getRemainingFireTicks(entity) <= 0) {
            return;
        }

        // 必须存活
        if (!entity.isEntityAlive()) {
            return;
        }

        // 每20tick（1秒）执行一次，减少heal事件触发频率
        if (entity.ticksExisted % HEAL_INTERVAL_TICKS.getInt() != 0) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        // 每秒恢复 0.1% × 等级 最大生命值（原本是每tick算1/20，现在1秒一次直接算总量）
        float healAmount = entity.getMaxHealth() * effectiveLevel * (float) HEAL_PER_LEVEL.get();

        if (healAmount > 0) {
            entity.heal(healAmount);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
