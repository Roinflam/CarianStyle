package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import pers.roinflam.carianstyle.base.entity.AbsorptionShield;

import javax.annotation.Nonnull;
import java.util.Stack;

/**
 * 伤害适配层的发出端：在 1.20.1 Forge 发 {@code LivingAttackEvent} / {@code LivingHurtEvent} / {@code LivingDamageEvent}
 * 的原位置发出本模组的三个同名事件。
 *
 * <h3>为什么要自己发</h3>
 * <p>
 * 本模组 170 多个伤害监听、100 多个模板覆写都依赖 1.20.1 这三个事件的精确时序：攻击阶段在一切检查之前、可取消；
 * 受伤阶段在护甲之前、改的伤害会被护甲减；伤害阶段在扣完吸收之后、改的是扣血量。
 * NeoForge 21.1 没有一一对应的事件：{@code LivingIncomingDamageEvent} 晚于无敌 / 已死 / 抗火检查、
 * 早于盾和无敌帧；{@code LivingDamageEvent.Pre} 晚于护甲、在吸收之前、不能取消。逐处改写等于每个附魔重新论证时序。
 * 三个挂点在 1.21.1 的代码里都还在（{@code hurt} 开头、{@code actuallyHurt} 开头、扣完吸收之后），
 * 所以用 mixin 原位发出，监听代码只改 import。
 * </p>
 *
 * <h3>先后顺序（1.21.1）</h3>
 * <p>
 * 攻击阶段 → {@code LivingIncomingDamageEvent} → 盾 → 冰冻 / 头盔 → 无敌帧 → 受伤阶段 → 护甲 → 魔法减免 →
 * {@code LivingDamageEvent.Pre} → 扣吸收 → 伤害阶段 → 扣血 → {@code LivingDamageEvent.Post}。
 * 别的模组照样收到 NeoForge 自己的事件，不受影响。
 * </p>
 *
 * <h3>伤害数值从哪来</h3>
 * <p>
 * NeoForge 在 {@code hurt} 里给每一下压一个 {@link DamageContainer}，之后整条流程以它的 {@code newDamage} 为准
 * （盾、无敌帧扣掉的部分都记在里面）。受伤、伤害阶段从栈顶容器读数、把结果写回，
 * 原版紧接着读的就是容器里的值。伤害阶段里嵌套 {@code hurt}（再打一下）会压自己的容器，栈顶永远是当前这一下。
 * </p>
 */
public final class DamageStages {

    private DamageStages() {
    }

    /**
     * 攻击阶段：{@code hurt} 开头、任何检查之前。
     *
     * @return true 表示被取消，{@code hurt} 直接返回 false（不写无敌时间、不压容器、NeoForge 的伤害事件都不发）
     */
    public static boolean attack(@Nonnull LivingEntity entity, @Nonnull DamageSource source, float amount) {
        return NeoForge.EVENT_BUS.post(new LivingAttackEvent(entity, source, amount)).isCanceled();
    }

    /**
     * 受伤阶段：{@code actuallyHurt} 开头（无敌帧已经写过），对应 1.20.1 {@code actuallyHurt} 里的 {@code onLivingHurt}。
     * <p>
     * 被取消或结果 ≤ 0 时把容器里的伤害设成 0 并返回 true，调用方跳过整个 {@code actuallyHurt}（1.20.1 是直接 {@code return}）；
     * 否则把结果写回容器，护甲、吸收照常按新值算。
     * {@code isInvulnerableTo} 为真时不发（1.20.1 的事件也在这个判断里面），容器栈是空的时候也不发
     * （别的模组绕过 {@code hurt} 直接调 {@code actuallyHurt}，原版自己下一行也会出错，别让崩溃栈指到这里）。
     * </p>
     *
     * @return true 表示中止这一下
     */
    public static boolean hurt(@Nonnull LivingEntity entity, @Nonnull DamageSource source,
                               @Nonnull Stack<DamageContainer> containers) {
        if (containers.isEmpty() || entity.isInvulnerableTo(source)) {
            return false;
        }
        DamageContainer container = containers.peek();
        LivingHurtEvent event = NeoForge.EVENT_BUS.post(new LivingHurtEvent(entity, source, container.getNewDamage()));
        if (event.isCanceled() || event.getAmount() <= 0) {
            container.setNewDamage(0);
            return true;
        }
        container.setNewDamage(event.getAmount());
        return false;
    }

    /**
     * 伤害阶段：{@code actuallyHurt} 里扣完吸收（{@code setAbsorptionAmount}）之后、扣血之前，
     * 对应 1.20.1 的 {@code onLivingDamage}。伤害为 0 也发；取消视为 0；结果写回容器，原版紧接着就按它扣血。
     * <p>
     * 发完调一次 {@link AbsorptionShield#afterAbsorb}：吸收被打掉一部分后，把本模组加的吸收上限缩回去。
     * </p>
     */
    public static void damage(@Nonnull LivingEntity entity, @Nonnull DamageSource source,
                              @Nonnull Stack<DamageContainer> containers) {
        if (containers.isEmpty()) {
            return;
        }
        DamageContainer container = containers.peek();
        LivingDamageEvent event = NeoForge.EVENT_BUS.post(new LivingDamageEvent(entity, source, container.getNewDamage()));
        container.setNewDamage(event.isCanceled() ? 0 : event.getAmount());
        AbsorptionShield.afterAbsorb(entity);
    }
}
