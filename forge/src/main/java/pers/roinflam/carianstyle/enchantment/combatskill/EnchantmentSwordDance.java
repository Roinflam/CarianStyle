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
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

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
        victim.invulnerableTime = victim.invulnerableDuration / 2;

        // 如果是玩家攻击，伤害乘以攻击冷却进度（最低0.4）
        if (ctx.isHolderPlayer()) {
            Player player = ctx.getHolderAsPlayer();
            float cooldownProgress = Math.max(player.getAttackStrengthScale(0.5F), 0.4f);
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