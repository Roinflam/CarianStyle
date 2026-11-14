package pers.roinflam.carianstyle.event.client.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent.Stage;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent;

/** {@link RenderLevelStageEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class RenderLevelStageEventImpl extends RenderLevelStageEvent {

    private final Stage stage;
    private final Camera camera;
    private final float partialTick;
    private final PoseStack poseStack;

    public RenderLevelStageEventImpl(Stage stage, Camera camera, float partialTick, PoseStack poseStack) {
        this.stage = stage;
        this.camera = camera;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
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
    public float getPartialTick() {
        return partialTick;
    }

    @Override
    public PoseStack getPoseStack() {
        return poseStack;
    }
}
