package pers.roinflam.carianstyle.event.client.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import pers.roinflam.carianstyle.event.client.RenderHandEvent;

/** {@link RenderHandEvent} 的字段型实现：Fabric 侧的发出点和测试台直接 new。 */
public final class RenderHandEventImpl extends RenderHandEvent {

    private final float partialTick;
    private final PoseStack poseStack;
    private final MultiBufferSource multiBufferSource;

    public RenderHandEventImpl(float partialTick, PoseStack poseStack, MultiBufferSource multiBufferSource) {
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.multiBufferSource = multiBufferSource;
    }

    @Override
    public float getPartialTick() {
        return partialTick;
    }

    @Override
    public PoseStack getPoseStack() {
        return poseStack;
    }

    @Override
    public MultiBufferSource getMultiBufferSource() {
        return multiBufferSource;
    }
}
