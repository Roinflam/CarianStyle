package pers.roinflam.carianstyle.forge.forward.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import pers.roinflam.carianstyle.bus.WeakEventRef;
import pers.roinflam.carianstyle.event.client.ClientPlayerNetworkEvent;
import pers.roinflam.carianstyle.event.client.ClientTickEvent;
import pers.roinflam.carianstyle.event.client.RenderHandEvent;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent;
import pers.roinflam.carianstyle.event.client.RenderLivingEvent;
import pers.roinflam.carianstyle.event.client.RenderPlayerEvent;

import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.forward;
import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.forwardTick;
import static pers.roinflam.carianstyle.forge.forward.ForgeForwarding.target;

/**
 * 客户端 / 渲染类 Forge 事件的直通包装和转发登记。只在客户端装，专用服上这个类不加载，它引用的客户端类型也就不会被碰到。
 */
public final class ForgeClientForwards {

    private ForgeClientForwards() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install() {
        forward(net.minecraftforge.client.event.RenderLevelStageEvent.class, RenderLevelStageEvent.class,
                LevelStage.class, LevelStage::new);
        forward(net.minecraftforge.client.event.RenderPlayerEvent.Pre.class, RenderPlayerEvent.Pre.class,
                PlayerPre.class, PlayerPre::new);
        // Forge 的生物渲染事件带类型参数，转发只按擦除后的类挂
        forward((Class) net.minecraftforge.client.event.RenderLivingEvent.Post.class, RenderLivingEvent.Post.class,
                LivingPost.class, e -> new LivingPost((net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?>) e));
        forward(net.minecraftforge.client.event.RenderHandEvent.class, RenderHandEvent.class, Hand.class, Hand::new);
        forwardTick(TickEvent.ClientTickEvent.class, null,
                target(ClientTickEvent.Post.class, ClientTickPost.class, ClientTickPost::new));
        forward(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class,
                ClientPlayerNetworkEvent.LoggingOut.class, LoggingOut.class, LoggingOut::new);
    }

    /** Forge 的阶段对象换成本模组的枚举值，没声明的阶段一律是 OTHER。 */
    static RenderLevelStageEvent.Stage mapStage(net.minecraftforge.client.event.RenderLevelStageEvent.Stage s) {
        if (s == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        }
        if (s == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        }
        if (s == net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        }
        return RenderLevelStageEvent.Stage.OTHER;
    }

    static final class LevelStage extends RenderLevelStageEvent {
        private final WeakEventRef<net.minecraftforge.client.event.RenderLevelStageEvent> e;

        LevelStage(net.minecraftforge.client.event.RenderLevelStageEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Stage getStage() {
            return mapStage(e.get().getStage());
        }

        @Override
        public Camera getCamera() {
            return e.get().getCamera();
        }

        @Override
        public float getPartialTick() {
            return e.get().getPartialTick();
        }

        @Override
        public PoseStack getPoseStack() {
            return e.get().getPoseStack();
        }
    }

    static final class PlayerPre extends RenderPlayerEvent.Pre {
        private final WeakEventRef<net.minecraftforge.client.event.RenderPlayerEvent.Pre> e;

        PlayerPre(net.minecraftforge.client.event.RenderPlayerEvent.Pre e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public Player getEntity() {
            return e.get().getEntity();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class LivingPost extends RenderLivingEvent.Post<LivingEntity, Object> {
        private final WeakEventRef<net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?>> e;

        LivingPost(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public LivingEntity getEntity() {
            return e.get().getEntity();
        }

        @Override
        public float getPartialTick() {
            return e.get().getPartialTick();
        }

        @Override
        public PoseStack getPoseStack() {
            return e.get().getPoseStack();
        }

        @Override
        public MultiBufferSource getMultiBufferSource() {
            return e.get().getMultiBufferSource();
        }
    }

    static final class Hand extends RenderHandEvent {
        private final WeakEventRef<net.minecraftforge.client.event.RenderHandEvent> e;

        Hand(net.minecraftforge.client.event.RenderHandEvent e) {
            this.e = new WeakEventRef<>(e);
        }

        @Override
        public float getPartialTick() {
            return e.get().getPartialTick();
        }

        @Override
        public PoseStack getPoseStack() {
            return e.get().getPoseStack();
        }

        @Override
        public MultiBufferSource getMultiBufferSource() {
            return e.get().getMultiBufferSource();
        }

        @Override
        public boolean isCanceled() {
            return e.get().isCanceled();
        }

        @Override
        public void setCanceled(boolean canceled) {
            e.get().setCanceled(canceled);
        }
    }

    static final class ClientTickPost extends ClientTickEvent.Post {
        // 没有成员可读，不用持有 Forge 事件
        ClientTickPost(TickEvent.ClientTickEvent e) {
        }
    }

    static final class LoggingOut extends ClientPlayerNetworkEvent.LoggingOut {
        LoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        }
    }
}
