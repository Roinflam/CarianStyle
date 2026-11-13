package pers.roinflam.carianstyle.event;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/** 药水效果的施加、移除、到期。 */
public abstract class MobEffectEvent extends LivingEvent {

    /** 涉及的效果实例；移除事件里可能为空（按效果类型移除而身上本来没有时）。 */
    public abstract MobEffectInstance getEffectInstance();

    /** 效果已判定可以施加、写进身上之前（新加和叠加都发）；{@code forceAddEffect} 不发，同 NeoForge。 */
    public abstract static class Added extends MobEffectEvent {

        @Override
        public abstract MobEffectInstance getEffectInstance();
    }

    /** 效果将被移除（按类型移除、全部清除；牛奶、图腾、蜂蜜都经过这里）。 */
    public abstract static class Remove extends MobEffectEvent {

        public abstract Holder<MobEffect> getEffect();
    }

    /** 效果到期、移除之前，只在服务端发。 */
    public abstract static class Expired extends MobEffectEvent {

        @Override
        public abstract MobEffectInstance getEffectInstance();
    }
}
