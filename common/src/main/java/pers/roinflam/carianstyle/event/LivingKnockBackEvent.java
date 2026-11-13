package pers.roinflam.carianstyle.event;

/** 生物将要被击退（{@code knockback} 第一行，击退抗性之前）。 */
public abstract class LivingKnockBackEvent extends LivingEvent {

    public abstract float getStrength();

    public abstract void setStrength(float strength);
}
