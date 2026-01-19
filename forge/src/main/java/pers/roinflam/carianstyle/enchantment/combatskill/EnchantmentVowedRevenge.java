package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 誓复仇附魔
 * <p>
 * 周围敌人越多，伤害越高（每个敌人增加 2.5% × 等级）
 * 如果攻击的是复仇目标，额外增加 5% × 等级 的伤害
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentVowedRevenge extends EnchantmentBase {

    public EnchantmentVowedRevenge() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getAttacker();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 获取周围敌人数量（修正：List<LivingEntity>）
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                level * 2,
                entityLivingBase -> !entityLivingBase.equals(attacker)
        );

        // 每个周围敌人增加 2.5% × 等级 的伤害
        float damageIncrease = ctx.getDamage() * level * entities.size() * 0.025f;
        ctx.addDamage(damageIncrease);

        // 如果攻击的是复仇目标，额外增加 5% × 等级 的伤害
        if (attacker.getLastHurtByMob() != null && attacker.getLastHurtByMob().equals(victim)) {
            float revengeDamage = ctx.getDamage() * level * 0.05f;
            ctx.addDamage(revengeDamage);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}