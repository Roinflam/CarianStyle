package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 伤害链「攻击阶段」：生物受到攻击、任何检查（无敌、已死、抗火）之前发出，可取消。
 * <p>
 * 1.21.1 的 NeoForge 没有和 1.20.1 Forge {@code LivingAttackEvent} 对应的事件
 * （最接近的 {@code LivingIncomingDamageEvent} 晚于无敌 / 已死 / 抗火检查，玩家受击时伤害也已按难度缩放过）。
 * 本模组在 {@code hurt} 头部自己发这个事件，类名、方法和 1.20.1 一样，监听代码只改 import。
 * </p>
 * <ul>
 *   <li>取消：{@code hurt} 直接返回 false，不写无敌时间、不进后面的受伤 / 伤害阶段；</li>
 *   <li>{@link #getAmount()} 是原始伤害，只读。</li>
 * </ul>
 * <p>
 * 发出端在 {@code event/damage/DamageStages} 和 {@code mixin/damage/} 两个 mixin 里。
 * 名字和 NeoForge 的 {@code LivingDamageEvent} 等撞了的，同一个文件两个都要用时写全名。
 * </p>
 */
public class LivingAttackEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private final float amount;

    public LivingAttackEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.amount = amount;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }
}
