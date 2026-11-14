package pers.roinflam.carianstyle.event;

import net.minecraft.world.entity.player.Player;

/**
 * 和玩家有关的事件；登录、下线、重生、切维度、复制数据几种照 NeoForge 的写法做成嵌套类，
 * 监听里 {@code PlayerEvent.PlayerLoggedInEvent} 这类写法不用改。
 * <p>
 * 几种生命周期事件的先后和 NeoForge 一致：登录 Join → LoggedIn；下线 LoggedOut → Leave；
 * 死亡重生 Leave → Clone → Join → Respawn；切维度 Leave → Join → ChangedDimension。
 * </p>
 */
public abstract class PlayerEvent extends LivingEvent {

    @Override
    public abstract Player getEntity();

    /** 玩家登录完成（{@code placeNewPlayer} 最后）。 */
    public abstract static class PlayerLoggedInEvent extends PlayerEvent {
    }

    /** 玩家下线（{@code PlayerList#remove} 第一行，存盘之前）。 */
    public abstract static class PlayerLoggedOutEvent extends PlayerEvent {
    }

    /** 玩家重生完成（{@code PlayerList#respawn} 最后）。 */
    public abstract static class PlayerRespawnEvent extends PlayerEvent {

        /** 是不是从末地回来（而不是死亡重生）。 */
        public abstract boolean isEndConquered();
    }

    /**
     * 玩家换了维度。
     * <p>
     * Fabric 侧比 NeoForge 发得早：NeoForge 在 {@code changeDimension} 最后、能力 / 世界信息 / 玩家信息 / 药水效果包都发完
     * 之后才发，Fabric 侧在维度切换触发器之后、这些包之前。这时玩家已经在新世界里、重生包已发，现有唯一的监听
     * （往客户端补发效果同步包）不受影响；以后有监听要求客户端已收到效果包，Fabric 侧的发出点要挪到 {@code changeDimension} 返回处。
     * </p>
     */
    public abstract static class PlayerChangedDimensionEvent extends PlayerEvent {
    }

    /** 死亡重生 / 从末地回来时，新玩家对象从旧对象复制完数据。{@link #getEntity()} 是新对象。 */
    public abstract static class Clone extends PlayerEvent {

        public abstract Player getOriginal();

        /** 是不是死亡重生（从末地回来为假）。 */
        public abstract boolean isWasDeath();
    }
}
