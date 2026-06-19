package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 誓复仇附魔
 * <p>
 * 周围敌人越多，伤害越高（每个敌人增加 2.5% × 等级）
 * 如果攻击的是复仇目标，额外增加 5% × 等级 的伤害
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止等级×2 直接当半径导致高等级时扫描范围爆炸。</li>
 *   <li>{@link #MAX_COUNTED_TARGETS}：计数目标数硬上限，防止密集怪物场景下 entities.size() 作乘数导致伤害倍率失控。</li>
 * </ul>
 *
 * <p>本附魔每次攻击都触发，触发频率极高，且 entities.size() 直接乘入伤害公式，
 * 原版等级 100 + 周围 200 个实体时伤害倍率 = 1 + 0.025 × 100 × 200 = 501 倍，
 * 既是性能风险也是数值风险，必须双重封顶。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentVowedRevenge extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块）：不管等级多高，最多搜索半径 8 方块 */
    private static final int MAX_SEARCH_RADIUS = 8;

    /** 计数目标数硬上限：防止密集怪物场景下 entities.size() 作乘数导致伤害/性能爆炸 */
    private static final int MAX_COUNTED_TARGETS = 20;

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

        // ⭐ v2.1：搜索半径硬上限，防止等级直接当半径
        // 原：level * 2（100级 = 200格）
        int searchRadius = Math.min(level * 2, MAX_SEARCH_RADIUS);

        // 获取周围敌人数量
        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                searchRadius,
                entityLivingBase -> !entityLivingBase.equals(attacker)
        );

        // ⭐ v2.1：计数目标数硬上限，防止 entities.size() 过大导致伤害倍率失控
        int countedTargets = Math.min(entities.size(), MAX_COUNTED_TARGETS);

        // 每个周围敌人增加 2.5% × 等级 的伤害（使用封顶后的数量）
        float damageIncrease = ctx.getDamage() * level * countedTargets * 0.025f;
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
