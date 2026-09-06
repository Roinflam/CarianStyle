package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 猩红腐败附魔
 * <p>
 * 武器附魔，攻击时施加腐败效果
 * 攻击时：
 * - 给目标施加猩红腐败效果（持续 = 等级 × 20秒）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "scarlet_rot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentFireGivesPower.class, EnchantmentFireDevoured.class}
)
public class EnchantmentScarletCorruption extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.scarlet_rot.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "scarlet_rot";

    /**
     * 每级猩红腐败的持续秒数
     * <p>默认 20，允许范围 1 ~ 300。</p>
     */
    private static final EnchantmentValues.Handle SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "seconds_per_level",
                    20, 1, 300);

    public EnchantmentScarletCorruption() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 修复：改为 onDamageAsAttackerLowest（作为攻击者时触发）
     */
    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 给被攻击的目标施加猩红腐败
        victim.addEffect(new MobEffectInstance(
                CarianStylePotion.SCARLET_ROT.get(),
                level * SECONDS_PER_LEVEL.getInt() * 20,  // 等级 × 20秒 × 20tick
                0
        ));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
