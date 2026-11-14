package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.event.LivingEvent;

/**
 * 伤害链「攻击阶段」：生物受到攻击、任何检查（无敌、已死、抗火）之前发出，可取消。
 * <ul>
 *   <li>取消：{@code hurt} 直接返回 false，不写无敌时间、不进后面的受伤 / 伤害阶段；</li>
 *   <li>{@link #getAmount()} 是原始伤害，只读。</li>
 * </ul>
 * <p>
 * Forge 上直通转发 Forge 自己的同名事件（读写、取消都落在 Forge 事件上），Fabric 上由 fabric 模块的伤害三段 mixin 发。
 * 名字和 Forge 的同名事件撞了，同一个文件两个都要用时写全名。
 * </p>
 */
public class LivingAttackEvent extends LivingEvent implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;
    private final float amount;

    public LivingAttackEvent(LivingEntity entity, DamageSource source, float amount) {
        this.entity = entity;
        this.source = source;
        this.amount = amount;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }
}
