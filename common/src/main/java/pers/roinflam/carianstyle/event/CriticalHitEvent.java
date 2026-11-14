package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.Entity;

/**
 * 玩家近战攻击判定暴击之后、乘暴击倍率之前。倍率初值照平台：原版暴击为 1.5，否则 1.0。
 * 附魔加成在乘倍率之后才加，同 NeoForge。
 */
public abstract class CriticalHitEvent extends PlayerEvent {

    public abstract Entity getTarget();

    /** 原版规则下这一击是不是暴击。 */
    public abstract boolean isVanillaCritical();

    public abstract float getDamageMultiplier();

    public abstract void setDamageMultiplier(float multiplier);
}
