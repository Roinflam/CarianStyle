package pers.roinflam.carianstyle.event.client.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.event.client.RenderLivingEvent;

/** {@link RenderLivingEvent.Post} 的字段型实现：Fabric 侧的发出点直接 new。 */
public final class RenderLivingPostEventImpl extends RenderLivingEvent.Post<LivingEntity, Object> {

    private final LivingEntity entity;
    private final float partialTick;
    private final PoseStack poseStack;
    private final MultiBufferSource multiBufferSource;

    public RenderLivingPostEventImpl(LivingEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource multiBufferSource) {
        this.entity = entity;
        this.partialTick = partialTick;
        this.poseStack = poseStack;
        this.multiBufferSource = multiBufferSource;
    }

    @Override
    public LivingEntity getEntity() {
        return entity;
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
