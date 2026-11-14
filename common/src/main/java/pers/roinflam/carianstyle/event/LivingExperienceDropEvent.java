package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.player.Player;

/**
 * 生物死亡将要掉经验球。
 * <p>
 * 末影龙的后段经验：NeoForge 用一个不过期的击杀玩家字段，原版没有，Fabric 侧这时 {@link #getAttackingPlayer()} 可能为空。
 * </p>
 */
public abstract class LivingExperienceDropEvent extends LivingEvent {

    /** 最后打它的玩家；不是玩家打死的为空。 */
    public abstract Player getAttackingPlayer();

    public abstract int getDroppedExperience();

    public abstract void setDroppedExperience(int experience);
}
