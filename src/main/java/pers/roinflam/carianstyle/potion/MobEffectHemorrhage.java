package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 出血药水效果
 * <p>
 * 效果：
 * - 每tick造成最大生命值×(7%+等级×1%)/30的直接扣血
 * - 可直接致死
 * </p>
 * <p>
 * 性能优化 v3.0：
 * 移除 SynchronizationTask(1) 延迟调用，改为直接在 applyEffectTick 中扣血。
 * 原版 Poison/Wither 效果也直接在效果 tick 中调用伤害，
 * 说明在此阶段修改血量是安全的。
 * </p>
 * <p>
 * 消除的开销：50个实体同时出血 = 原来每秒创建1000个匿名任务对象 → 现在0个。
 * </p>
 *
 * <h3>v3.1 修复：客户端崩溃（与 {@link MobEffectIncision} 完全同源）</h3>
 * <p>
 * <b>问题：</b>{@link #applyEffectTick} 此前缺少客户端守卫。原版的效果 tick
 * 在<b>逻辑服务端与逻辑客户端两侧都会执行</b>，因此扣血与致死逻辑都被跑了两遍。
 * <b>单人游戏同样如此</b>——单机是「一个进程内同时跑客户端与内置服务端」，
 * 玩家实体在服务端世界与客户端世界各有一个实例。客户端那份实例自己走致死逻辑，
 * 会在只有服务端才有的掉落 / 死亡流程里出错。
 * </p>
 *
 * <h3>v3.1 修复：致死路径绕过伤害管线</h3>
 * <p>
 * 致死分支此前调用 {@code EntityLivingUtil.kill}，直改血量后直接走死亡，
 * <b>完全跳过伤害管线</b>。后果是：不触发 {@code LivingHurtEvent} / {@code LivingDamageEvent}、
 * <b>不给不死图腾任何生效机会</b>、各类免死 / 护盾类附魔全部失效。
 * </p>
 * <p>
 * 现改为走原版伤害管线（1.12.2 是 {@code attackEntityFrom}），伤害量取最大生命的 10 倍以确保必定致命。
 * 这样死亡会正常触发全套事件与图腾判定，战利品掉落也只会在服务端发生。
 * 出血伤害源是绝对伤害（{@code NewDamageSource.hemorrhage}），抗性提升 V 挡不住这一击。
 * </p>
 * <p>
 * <b>逐 tick 的持续扣血仍保留 {@code setHealth} 直扣</b>，未改为走伤害——
 * 每秒 20 次走完整伤害管线会触发全部受击类附魔（因果律累积、各类反伤等）各 20 次，
 * 既是性能灾难也会让机制行为完全跑偏。持续掉血本就是「持续失血」而非「受到攻击」，
 * 直扣血量是正确的语义。
 * </p>
 *
 * @author RoinFlam
 * @version 3.1
 */
public class MobEffectHemorrhage extends IconBase {

    /**
     * 致死伤害倍率（× 最大生命）。
     * <p>取足够大的值确保穿透护甲与各类减伤后仍然致命，同时避免使用
     * {@code Float.MAX_VALUE} 在后续伤害计算中产生溢出 / NaN。</p>
     */
    private static final float LETHAL_DAMAGE_MULTIPLIER = 10.0f;

    /**
     * 构造函数
     *
     * @param isBadEffectIn 是否为负面效果
     * @param liquidColorIn 液体颜色
     */
    public MobEffectHemorrhage(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
    }

    /**
     * 每tick应用出血伤害（直接扣血，不再创建延迟任务）
     * <p>
     * <b>v3.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 若不加守卫，客户端会自行走致死逻辑（详见类注释的「v3.1 修复」小节）。
     * </p>
     *
     * @param entityLivingBaseIn 受影响的实体
     * @param amplifier          效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        // v3.1 客户端守卫：扣血与致死判定只在逻辑服务端执行一次。
        if (entityLivingBaseIn.world.isRemote) {
            return;
        }

        // 回溯无敌期间不掉血：直扣血量拦不到，致死那一击的 10 倍最大生命还会被算进储存伤害
        if (EnchantmentTimeReversal.isReversing(entityLivingBaseIn)) {
            return;
        }

        float damage = entityLivingBaseIn.getMaxHealth() * (0.07f + 0.01f * amplifier) / 30;

        if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
            // 常规掉血：直扣血量，不走伤害管线。
            // 每秒 20 次走伤害会让全部受击类附魔各触发 20 次，
            // 既是性能灾难也会让机制行为跑偏；持续失血语义上不是「受到攻击」。
            entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
        } else {
            // v3.1 致死路径：改走原版伤害管线，不再用 EntityLivingUtil.kill 强制处决。
            // 原实现会绕过伤害管线，导致不死图腾与各类免死机制全部失效。
            entityLivingBaseIn.attackEntityFrom(
                    NewDamageSource.hemorrhage(entityLivingBaseIn.world),
                    entityLivingBaseIn.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER
            );
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/hemorrhage.png");
    }
}
