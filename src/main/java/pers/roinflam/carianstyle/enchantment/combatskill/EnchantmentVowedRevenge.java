package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 复仇誓言附魔
 *
 * 攻击时周围 等级×2 格（至多 8 格）内每有一个生物（含目标，至多计 20 个），伤害 +等级×2.5%；
 * 目标是 5 秒内最后攻击过自己的生物时，再 +等级×5%。
 * <p>以前半径和计入数量都不封顶，高等级在怪堆里倍率失控。</p>
 */
@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentVowedRevenge extends EnchantmentBase {

    /** 搜索半径上限（格） */
    private static final int MAX_SEARCH_RADIUS = 8;
    /** 计入的生物数上限 */
    private static final int MAX_COUNTED_TARGETS = 20;
    /** 每个生物每级的增伤 */
    private static final float DAMAGE_PER_TARGET_PER_LEVEL = 0.025F;
    /** 复仇目标每级的额外增伤 */
    private static final float REVENGE_DAMAGE_PER_LEVEL = 0.05F;

    public EnchantmentVowedRevenge() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getAttacker();
        EntityLivingBase victim = ctx.getVictim();

        if (attacker == null || victim == null) {
            return;
        }

        int searchRadius = Math.min(level * 2, MAX_SEARCH_RADIUS);

        List<Entity> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                attacker,
                searchRadius,
                entityLivingBase -> !entityLivingBase.equals(attacker)
        );

        int countedTargets = Math.min(entities.size(), MAX_COUNTED_TARGETS);

        float damageIncrease = ctx.getDamage() * level * countedTargets * DAMAGE_PER_TARGET_PER_LEVEL;
        ctx.addDamage(damageIncrease);

        if (attacker.getRevengeTarget() != null && attacker.getRevengeTarget().equals(victim)) {
            float revengeDamage = ctx.getDamage() * level * REVENGE_DAMAGE_PER_LEVEL;
            ctx.addDamage(revengeDamage);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}