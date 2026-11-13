package pers.roinflam.carianstyle.event.client.impl;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent.Stage;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent;

/** {@link RenderLevelStageEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class RenderLevelStageEventImpl extends RenderLevelStageEvent {

    private final Stage stage;
    private final Camera camera;
    private final DeltaTracker partialTick;

    public RenderLevelStageEventImpl(Stage stage, Camera camera, DeltaTracker partialTick) {
        this.stage = stage;
        this.camera = camera;
        this.partialTick = partialTick;
    }

    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public Camera getCamera() {
        return camera;
    }

    @Override
    public DeltaTracker getPartialTick() {
        return partialTick;
    }
}
