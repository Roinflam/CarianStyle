package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.ICancellableEvent;
import pers.roinflam.carianstyle.event.LivingEvent;

/**
 * 伤害链「伤害阶段」：护甲、抗性减免和扣吸收都做完之后、扣血之前发出，可取消。
 * <p>
 * 此时受击者的吸收值已经扣过（实体状态是扣之后的），事件只改「要扣的血量」。伤害为 0 时照样发；取消视为 0。
 * Forge 上直通转发 Forge 的同名事件，Fabric 上由伤害三段 mixin 在同一位置发。
 * </p>
 */
public class LivingDamageEvent extends LivingEvent implements ICancellableEvent {
    private final LivingEntity entity;
    private final DamageSource source;
    private float amount;

    public LivingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
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
