package pers.roinflam.carianstyle.event.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

/**
 * 伤害链「受伤阶段」：{@code actuallyHurt} 开头、护甲和抗性减免之前发出，可取消。
 * <p>
 * 对应 1.20.1 Forge 的 {@code LivingHurtEvent}，位置一样（盾、冰冻 / 头盔、无敌帧之后，护甲之前），
 * 类名和方法也一样，监听代码只改 import：
 * </p>
 * <ul>
 *   <li>在这里加的伤害会被护甲、抗性减掉；</li>
 *   <li>取消或把伤害改到 0 及以下，这一下不扣血（击退、受击音效照常，和 1.20.1 一致）；</li>
 *   <li>{@code isInvulnerableTo} 为真时不发。</li>
 * </ul>
 */
public class LivingHurtEvent extends LivingEvent implements ICancellableEvent {
    private final DamageSource source;
    private float amount;

    public LivingHurtEvent(LivingEntity entity, DamageSource source, float amount) {
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
