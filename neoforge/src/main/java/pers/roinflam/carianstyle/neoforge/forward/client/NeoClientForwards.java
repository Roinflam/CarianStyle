package pers.roinflam.carianstyle.neoforge.forward.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.bus.WeakEventRef;
import pers.roinflam.carianstyle.event.client.ClientPlayerNetworkEvent;
import pers.roinflam.carianstyle.event.client.ClientTickEvent;
import pers.roinflam.carianstyle.event.client.RenderHandEvent;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent;
import pers.roinflam.carianstyle.event.client.RenderLivingEvent;
import pers.roinflam.carianstyle.event.client.RenderPlayerEvent;

import static pers.roinflam.carianstyle.neoforge.forward.NeoForwarding.forward;

/**
 * 客户端 / 渲染类平台事件的直通包装和转发登记。只在客户端装（{@code NeoForwarding.install} 按运行端判断），
 * 专用服上这个类不加载，它引用的客户端类型也就不会被碰到。
 */
public final class NeoClientForwards {

    private NeoClientForwards() {
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void install() {
        forward(net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, RenderLevelStageEvent.class,
                LevelStage.class, LevelStage::new);
        forward(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre.class, RenderPlayerEvent.Pre.class,
                PlayerPre.class, PlayerPre::new);
        // NeoForge 的生物渲染事件带类型参数，转发只按擦除后的类挂
        forward((Class) net.neoforged.neoforge.client.event.RenderLivingEvent.Post.class, RenderLivingEvent.Post.class,
                LivingPost.class, e -> new LivingPost((net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?>) e));
        forward(net.neoforged.neoforge.client.event.RenderHandEvent.class, RenderHandEvent.class, Hand.class, Hand::new);
        forward(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class, ClientTickEvent.Post.class,
                ClientTickPost.class, ClientTickPost::new);
        forward(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut.class,
                ClientPlayerNetworkEvent.LoggingOut.class, LoggingOut.class, LoggingOut::new);
    }

    /** NeoForge 的阶段对象换成本模组的四个枚举值，没声明的阶段一律是 OTHER。 */
    static RenderLevelStageEvent.Stage mapStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage s) {
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS;
        }
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            return RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS;
        }
        if (s == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        }
        return RenderLevelStageEvent.Stage.OTHER;
    }

    static final class LevelStage extends RenderLevelStageEvent {
        private final WeakEventRef<net.neoforged.neoforge.client.event.RenderLevelStageEvent> e;

        LevelStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent e) {
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
        public DeltaTracker getPartialTick() {
            return e.get().getPartialTick();
        }
    }

    static final class PlayerPre extends RenderPlayerEvent.Pre {
        private final WeakEventRef<net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre> e;

        PlayerPre(net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre e) {
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
        private final WeakEventRef<net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?>> e;

        LivingPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> e) {
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
        private final WeakEventRef<net.neoforged.neoforge.client.event.RenderHandEvent> e;

        Hand(net.neoforged.neoforge.client.event.RenderHandEvent e) {
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
        // 没有成员可读，不用持有平台事件
        ClientTickPost(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        }
    }

    static final class LoggingOut extends ClientPlayerNetworkEvent.LoggingOut {
        LoggingOut(net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        }
    }
}
