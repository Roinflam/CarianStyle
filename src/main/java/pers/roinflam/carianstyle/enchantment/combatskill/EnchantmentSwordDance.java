package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper;

import javax.annotation.Nonnull;

/**
 * 剑舞附魔
 * <p>
 * 减少敌人无敌帧（减半），可快速连续造成伤害
 * 如果是玩家攻击，伤害 × 攻击冷却进度（最低0.4倍）
 * </p>
 * <p>
 * 1.12.2 版：原版在造成伤害之前就清零了攻击冷却，受伤事件里现读 {@code getCooledAttackStrength} 恒为 0，
 * 这里读 {@link AttackCooldownHelper#getChargeSnapshot} 记下的出手前蓄力，和 1.20.1 读到的是同一个值。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "sword_dance",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentSwordDance extends EnchantmentBase {

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
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getVictim();
        if (victim == null) {
            return;
        }

        // 减少受害者无敌时间
        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime / INVULNERABLE_DIVISOR.get());

        // 如果是玩家攻击，伤害乘以攻击冷却进度（最低0.4）
        if (ctx.isHolderPlayer()) {
            EntityPlayer player = ctx.getHolderAsPlayer();
            float cooldownProgress = Math.max(AttackCooldownHelper.getChargeSnapshot(player),
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
