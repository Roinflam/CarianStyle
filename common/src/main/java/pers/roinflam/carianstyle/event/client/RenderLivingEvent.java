package pers.roinflam.carianstyle.event.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.bus.Event;

/**
 * 生物模型渲染。类型参数照 NeoForge 留着两个，只为监听里的 {@code RenderLivingEvent.Post<?, ?>} 写法不用改，本身不约束什么。
 */
public abstract class RenderLivingEvent<T extends LivingEntity, M> extends Event {

    public abstract LivingEntity getEntity();

    public abstract float getPartialTick();

    public abstract PoseStack getPoseStack();

    public abstract MultiBufferSource getMultiBufferSource();

    /** 生物渲染器画完之后（玩家经父类也到这里）。 */
    public abstract static class Post<T extends LivingEntity, M> extends RenderLivingEvent<T, M> {
    }
}
