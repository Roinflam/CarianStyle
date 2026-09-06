package pers.roinflam.carianstyle.enchantment.combatskill;

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
 * 剑舞附魔
 * <p>
 * 减少敌人无敌帧（减半），可快速连续造成伤害
 * 如果是玩家攻击，伤害 × 攻击冷却进度（最低0.4倍）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "sword_dance",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentSwordDance extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.sword_dance.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sword_dance";

    /**
     * 命中后无敌帧的缩短倍数（2 表示减半，越大攻击频率越高）
     * <p>默认 2.0，允许范围 1.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle INVULNERABLE_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "invulnerable_divisor",
                    2.0D, 1.0D, 20.0D);

    /**
     * 蓄力进度的下限：即使连点也至少按此比例结算伤害
     * <p>默认 0.4，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle ATTACK_SCALE_FLOOR =
            EnchantmentValues.define(VALUE_ID, "attack_scale_floor",
                    0.4D, 0.0D, 1.0D);

    public EnchantmentSwordDance() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 减少受害者无敌时间（1.20.1: invulnerableTime）
        victim.invulnerableTime = (int) (victim.invulnerableDuration / INVULNERABLE_DIVISOR.get());

        // 如果是玩家攻击，伤害乘以攻击冷却进度（最低0.4）
        if (ctx.isHolderPlayer()) {
            Player player = ctx.getHolderAsPlayer();
            float cooldownProgress = Math.max(player.getAttackStrengthScale(0.5F),
                (float) ATTACK_SCALE_FLOOR.get());
            ctx.multiplyDamage(cooldownProgress);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
