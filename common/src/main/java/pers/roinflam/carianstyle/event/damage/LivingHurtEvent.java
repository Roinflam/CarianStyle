package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.event.LivingEvent;

/**
 * 伤害链「受伤阶段」：{@code actuallyHurt} 开头、护甲和抗性减免之前发出，可取消。
 * <ul>
 *   <li>在这里加的伤害会被护甲、抗性减掉；</li>
 *   <li>取消或把伤害改到 0 及以下，这一下不扣血（击退、受击音效照常）；</li>
 *   <li>{@code isInvulnerableTo} 为真时不发。</li>
 * </ul>
 * <p>Forge 上直通转发 Forge 的同名事件，Fabric 上由伤害三段 mixin 在同一位置发。</p>
 */
public class LivingHurtEvent extends LivingEvent implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;

    public LivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
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

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
