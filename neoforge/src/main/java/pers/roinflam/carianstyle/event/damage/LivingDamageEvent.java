package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 伤害链「伤害阶段」：护甲、抗性减免和扣吸收都做完之后、扣血之前发出，可取消。
 * <p>
 * 对应 1.20.1 Forge 的 {@code LivingDamageEvent}：此时受击者的吸收值已经扣过（实体状态是扣之后的），
 * 事件只改「要扣的血量」。伤害为 0 时照样发；取消视为 0。类名和方法和 1.20.1 一样，监听代码只改 import。
 * </p>
 * <p>
 * <b>注意同名：</b>NeoForge 也有一个 {@code net.neoforged.neoforge.event.entity.living.LivingDamageEvent}
 * （抽象类，是 {@code Pre} / {@code Post} 的父类），和这里不是一回事；同一个文件两个都要用时写全名。
 * </p>
 */
public class LivingDamageEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;

    public LivingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
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

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
