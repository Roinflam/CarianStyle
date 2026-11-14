package pers.roinflam.carianstyle.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import pers.roinflam.carianstyle.bus.Event;
import pers.roinflam.carianstyle.bus.ICancellableEvent;

/** 第一人称每只手渲染之前各发一次；取消即不画这只手。 */
public abstract class RenderHandEvent extends Event implements ICancellableEvent {

    public abstract float getPartialTick();

    public abstract PoseStack getPoseStack();

    public abstract MultiBufferSource getMultiBufferSource();
}
