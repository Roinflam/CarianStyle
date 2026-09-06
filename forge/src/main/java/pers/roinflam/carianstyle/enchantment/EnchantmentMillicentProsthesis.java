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
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 米莉森义肢附魔
 * <p>
 * 武器附魔，连续攻击增强
 * 攻击时：
 * - 叠加攻击增益效果（最多叠到等级×7层）
 * - 满层后刷新为等级×16层，持续60tick
 * 伤害加成：
 * - 满层时增伤10%×等级
 * - 未满层时增伤5%×等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "millicent_prosthesis",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class,
                EnchantmentVicDragonThunder.class,
                EnchantmentDarkMoon.class,
                EnchantmentRedFeatheredBranchsword.class,
                EnchantmentBlueFeatheredBranchsword.class
        }
)
public class EnchantmentMillicentProsthesis extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.millicent_prosthesis.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "millicent_prosthesis";

    /**
     * 叠层满时每级的额外伤害倍率
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_FULL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level_full",
                    0.1D, 0.0D, 5.0D);

    /**
     * 叠层未满时每级的额外伤害倍率
     * <p>默认 0.05，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL_PARTIAL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level_partial",
                    0.05D, 0.0D, 5.0D);

    public EnchantmentMillicentProsthesis() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onAttackLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        // 玩家必须是刚挥动武器
        if (attacker instanceof Player) {
            if (!isJustSwung((Player) attacker)) {
                return;
            }
        }

        // 获取当前攻击提升等级
        int currentAmplifier = DynamicAttributeManager.getAmplifier(attacker, DynamicAttributes.ATTACK_BOOST);

        if (currentAmplifier < 0) {
            // 没有效果，初始化
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.ATTACK_BOOST.createInstance(30, level - 1));
        } else if (currentAmplifier < level * 7 - 1) {
            // 未满层，叠加
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.ATTACK_BOOST.createInstance(30, currentAmplifier + level));
        } else {
            // 满层，刷新为更高层
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.ATTACK_BOOST.createInstance(60, level * 16 - 1));
        }
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        // 获取当前攻击提升等级
        int currentAmplifier = DynamicAttributeManager.getAmplifier(attacker, DynamicAttributes.ATTACK_BOOST);

        if (currentAmplifier >= level * 7 - 1) {
            // 满层：增伤10%×等级
            ctx.addDamage(ctx.getDamage() * level * (float) DAMAGE_PER_LEVEL_FULL.get());
        } else if (currentAmplifier >= 0) {
            // 未满层：增伤5%×等级
            ctx.addDamage(ctx.getDamage() * level * (float) DAMAGE_PER_LEVEL_PARTIAL.get());
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
