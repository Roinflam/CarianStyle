package pers.roinflam.carianstyle.event;

import net.minecraft.world.level.Level;

/**
 * 实体将要加入世界（含读区块、世界生成、玩家登录 / 重生 / 切维度）。
 * <p>
 * NeoForge 两端都发；Fabric 侧只在服务端发（现有监听都判了端），以后有客户端监听要另补 {@code ClientLevel} 的发出点。
 * 不声明取消：本模组不取消它；平台上别的模组取消之后，本模组后面的监听照样跳过。
 * </p>
 */
public abstract class EntityJoinLevelEvent extends EntityEvent {

    public abstract Level getLevel();

    /** 是不是从存档读出来的（而不是新生成 / 新召唤的）。 */
    public abstract boolean loadedFromDisk();
}
