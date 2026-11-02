package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * PORT-STUB(E1)
 *
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
 * <p>v2.3：玩家必须满蓄力疾跑攻击才触发（急停、缓慢、挑飞、增伤整段跳过），怪物持有者不受影响</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@Deprecated
public class EnchantmentLungeUp extends EnchantmentBase {
    private static final String VALUE_ID = "lunge_up";
    private static final EnchantmentValues.Handle KNOCKUP_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "knockup_per_level",
                    0.3D, 0.0D, 3.0D);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.15D, 0.0D, 5.0D);
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

