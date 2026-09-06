package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 魔力蝎符附魔
 * <p>
 * 效果：
 * - 造成的魔法伤害增加 10% × 等级
 * - 造成魔法伤害时恢复自身已损失生命值 1% × 等级
 * - 受到物理伤害时伤害增加 10% × 等级
 * - 最大等级：5
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "magic_scorpion_charm",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
public class EnchantmentMagicScorpionCharm extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.magic_scorpion_charm.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "magic_scorpion_charm";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的伤害倍率加成
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);

    /**
     * 每级按已损失生命回复的比例
     * <p>默认 0.01，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.01D, 0.0D, 1.0D);

    public EnchantmentMagicScorpionCharm() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        DamageSource source = ctx.getDamageSource();

        // 必须是魔法伤害
        if (!DamageSourceUtil.isMagicDamage(source)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 增加魔法伤害 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * (float) DAMAGE_PER_LEVEL.get());

        // 恢复已损失生命值 1% × 等级
        float maxHealth = attacker.getMaxHealth();
        float currentHealth = attacker.getHealth();
        float lostHealth = maxHealth - currentHealth;

        if (lostHealth > 0) {
            float healAmount = lostHealth * effectiveLevel * (float) HEAL_PER_LEVEL.get();
            attacker.heal(healAmount);
        }
    }

    @Override
    protected void onHurtAsVictimHighest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource source = ctx.getDamageSource();

        // 必须是物理伤害
        if (!DamageSourceUtil.isPhysicalDamage(source)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 受到的物理伤害增加 10% × 等级
        ctx.multiplyDamage(1 + effectiveLevel * (float) DAMAGE_PER_LEVEL.get());
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
