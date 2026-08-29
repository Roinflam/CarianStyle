package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 突刺附魔
 * <p>
 * 疾跑攻击时触发：停止疾跑、短暂缓慢、5tick后击飞敌人、增伤+15%×等级
 * </p>
 * <p>
 * 修复记录：
 * - 延迟任务中增加victim存活检查，避免对已死亡实体操作
 * </p>
 * <p>v2.2：触发时播放「上挑弧 + 地面急停尘环」自绘特效</p>
 *
 * <h3>v2.2 为什么在触发瞬间就播，而不是等 5 tick 后的击飞</h3>
 * <p>
 * 特效总时长 600ms（12 tick），已经完整覆盖了「急停 → 蓄力 → 5 tick 后挑起」的全过程；
 * 而<b>玩家需要在急停的那一刻就得到反馈</b>——那是他的冲刺被中断、
 * 且被强制施加缓慢的时刻，没有提示的话会读成「卡了一下」。
 * 上挑弧本身在特效的前 35% 扫完，节奏上正好压在击飞发生之前。
 * </p>
 * <p>
 * 顺带：放在触发处而不是延迟任务里，也就不必操心「延迟期间目标死了 / 世界卸载了」
 * 这类边界——那正是 v2.1 修复过的坑。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "lunge_up",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentLungeUp extends EnchantmentBase {

    public EnchantmentLungeUp() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 必须疾跑中
        if (!attacker.isSprinting()) {
            return;
        }

        // 停止疾跑
        attacker.setSprinting(false);

        // ⭐ v2.2：播放「上挑弧 + 地面急停尘环」自绘特效。
        // 位置取受击者、朝向取攻击者；在急停这一刻就播，理由见类注释
        if (attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.lungeUp(serverLevel, attacker, victim);
        }

        // 施加缓慢效果
        attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 6));

        // 5tick后击飞敌人
        new SynchronizationTask(5) {
            @Override
            public void run() {
                // 修复：同时检查攻击者和受害者是否存活
                if (!attacker.isAlive() || !victim.isAlive()) {
                    return;
                }

                victim.setDeltaMovement(
                        victim.getDeltaMovement().x,
                        level * 0.3,
                        victim.getDeltaMovement().z
                );
                victim.hurtMarked = true;
            }
        }.start();

        // 增伤 +15% × 等级
        ctx.addDamage(ctx.getDamage() * level * 0.15f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
