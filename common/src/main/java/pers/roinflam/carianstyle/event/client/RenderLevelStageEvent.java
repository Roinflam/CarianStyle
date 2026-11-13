package pers.roinflam.carianstyle.event.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import pers.roinflam.carianstyle.bus.Event;

/**
 * 世界渲染的几个阶段点。每个阶段每帧发一次，监听按 {@link #getStage()} 挑自己要的阶段。
 * <p>
 * 只声明三个平台都发得出的阶段，其余平台阶段一律转成 {@link Stage#OTHER}（火焰渲染靠「别的阶段关窗」，所以 OTHER 也要发）。
 * 以后要用新阶段，编译期就会发现得给每个平台补发出点。1.21.1 NeoForge 在这里不给 PoseStack，这里也不声明。
 * </p>
 */
public abstract class RenderLevelStageEvent extends Event {

    public abstract Stage getStage();

    public abstract Camera getCamera();

    public abstract DeltaTracker getPartialTick();

    public enum Stage {
        AFTER_CUTOUT_BLOCKS,
        AFTER_ENTITIES,
        AFTER_TRANSLUCENT_BLOCKS,
        OTHER
    }
}
