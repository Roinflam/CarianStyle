package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 剑舞附魔
 *
 * 减少敌人无敌帧（减半），可快速连续造成伤害
 * 如果是玩家攻击，伤害 × 攻击冷却进度（最低0.4倍）
 */
@AutoRegisterEnchantment(
        id = "sword_dance",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        forceTreasure = true
)
public class EnchantmentSwordDance extends EnchantmentBase {

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
        victim.hurtResistantTime = victim.maxHurtResistantTime / 2;

        // 如果是玩家攻击，伤害乘以攻击冷却进度（最低0.4）
        if (ctx.isHolderPlayer()) {
            EntityPlayer player = ctx.getHolderAsPlayer();
            float cooldownProgress = Math.max(EntityLivingUtil.getTicksSinceLastSwing(player), 0.4f);
            ctx.multiplyDamage(cooldownProgress);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}