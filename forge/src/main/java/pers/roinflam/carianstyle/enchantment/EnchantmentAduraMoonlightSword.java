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
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 阿杜拉月光剑附魔
 * <p>
 * 攻击变为魔法伤害，对目标周围敌人施加冻伤
 * 白天：叠加+1等级，夜晚：叠加+2等级
 * </p>
 *
 * <h3>性能安全上限（v2.1 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止高等级附魔（如 100 级）
 *       直接把等级当半径，导致 100 格搜索扫过大量实体。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下
 *       对大量实体施加效果导致事件风暴。</li>
 * </ul>
 *
 * <p>本附魔每次攻击触发，触发频率极高，必须严格封顶。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "adura_moonlight_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentEpilepsyFire.class,
                EnchantmentEatShit.class,
                EnchantmentHypnoticSmoke.class,
                EnchantmentFireGivesPower.class,
                EnchantmentFireDevoured.class
        }
)
public class EnchantmentAduraMoonlightSword extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块）：不管等级多高，最多搜索半径 6 方块 */
    private static final int MAX_SEARCH_RADIUS = 6;

    /** 单次触发最大命中目标数：防止密集怪物场景下事件风暴 */
    private static final int MAX_TARGETS = 16;

    public EnchantmentAduraMoonlightSword() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 修复：改用 Normal 优先级
     */
    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑，非玩家直接触发
        if (ctx.isHolderPlayer()) {
            if (ctx.getHolderAsPlayer().getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        // 伤害变为魔法伤害
        if (ctx.getDamageSource() != null) {
            pers.roinflam.carianstyle.utils.util.DamageSourceUtil.setMagicDamage(ctx.getDamageSource());
        }

        // ⭐ v2.1：搜索半径硬上限，防止等级直接当半径
        // 原：effectiveLevel（100级 = 100格）
        int searchRadius = Math.min(effectiveLevel, MAX_SEARCH_RADIUS);

        // 获取目标周围的敌人
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(attacker)
        );

        // 施加冻伤效果
        boolean isNight = !attacker.level().isDay();
        int stackIncrease = isNight ? 2 : 1;
        int initialLevel = isNight ? 1 : 0;

        // ⭐ v2.1：命中数量硬上限，防止密集怪物场景下事件风暴
        int hitCount = 0;
        for (LivingEntity entity : nearbyEntities) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }

            MobEffectInstance existingEffect = entity.getEffect(CarianStylePotion.FROSTBITE.get());

            int newLevel;
            if (existingEffect != null) {
                newLevel = Math.min(existingEffect.getAmplifier() + stackIncrease, 9);
            } else {
                newLevel = initialLevel;
            }

            entity.addEffect(new MobEffectInstance(CarianStylePotion.FROSTBITE.get(), 200, newLevel));
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
