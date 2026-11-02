package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.world.WorldServer;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import javax.annotation.Nonnull;

/**
 * 狮子斩附魔
 * <p>
 * 20%概率触发：伤害无视护甲 + 增伤+15%×等级
 * <p>
 * <p>v2.1：触发时在目标身上播放「三道爪痕」自绘特效</p>
 *
 * <h3>v2.1 为什么特效画在目标身上</h3>
 * <p>
 * 爪痕是留在<b>被抓的那一方</b>身上的，而爪痕所在的平面必须正对着挥爪的人。
 * 因此 {@code lionClaw} 的<b>位置取受击者、朝向取攻击者</b>——
 * 两者取自不同实体。若图省事全用攻击者，爪痕会浮在自己胸前，完全读不通。
 * </p>
 * <p>
 * 1.12.2 版：「无视护甲」在受伤事件里才打上（{@link DamageSourceUtil#setBypassesArmor}），
 * 这时盾牌那一步已经过了，所以举盾照样挡得住，和 1.20.1 一致。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "lion_claw",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentLionClaw extends EnchantmentBase {

    public EnchantmentLionClaw() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        // 20%概率触发
        if (!RandomUtil.percentageChance(20)) {
            return;
        }

        DamageSourceUtil.setBypassesArmor(ctx.getDamageSource());
        // 基础增伤15%
        ctx.addDamage(ctx.getDamage() * (level * 0.15f));

        // v2.1：播放「三道爪痕」自绘特效。
        // 位置取受击者、朝向取攻击者（详见类注释）
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();
        if (victim != null && attacker.world instanceof WorldServer) {
            CarianStyleCombatArtEffects.lionClaw((WorldServer) attacker.world, attacker, victim);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
