// 文件：EnchantmentLionClaw.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentLionClaw.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

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
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "lion_claw",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentLionClaw extends EnchantmentBase {

    public EnchantmentLionClaw() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        // 20%概率触发
        if (!RandomUtil.percentageChance(20)) {
            return;
        }

        DamageSourceUtil.setBypassesArmor(ctx.getDamageSource());
        // 基础增伤15%
        ctx.addDamage(ctx.getDamage() * (level * 0.15f));

        // ⭐ v2.1：播放「三道爪痕」自绘特效。
        // 位置取受击者、朝向取攻击者（详见类注释）
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        if (victim != null && attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.lionClaw(serverLevel, attacker, victim);
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
