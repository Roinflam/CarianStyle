package pers.roinflam.carianstyle.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.client.ClientPlayerNetworkEvent;
import pers.roinflam.carianstyle.event.client.ClientTickEvent;
import pers.roinflam.carianstyle.event.client.RenderHandEvent;
import pers.roinflam.carianstyle.event.client.RenderLevelStageEvent;
import pers.roinflam.carianstyle.event.client.RenderLivingEvent;
import pers.roinflam.carianstyle.event.client.RenderPlayerEvent;
import pers.roinflam.carianstyle.event.client.impl.ClientLoggingOutEventImpl;
import pers.roinflam.carianstyle.event.client.impl.ClientTickPostEventImpl;
import pers.roinflam.carianstyle.event.client.impl.RenderHandEventImpl;
import pers.roinflam.carianstyle.event.client.impl.RenderLevelStageEventImpl;
import pers.roinflam.carianstyle.event.client.impl.RenderLivingPostEventImpl;
import pers.roinflam.carianstyle.event.client.impl.RenderPlayerPreEventImpl;

/**
 * 把客户端 / 渲染相关的 Fabric API 回调和 {@code carianstyle.fabric.client.mixins.json}
 * 里的 mixin 接到模组自带的事件总线上。只由客户端入口调用；mixin 里只取参数、调这里的静态方法，
 * 事件怎么建、怎么发都在这一个类里。
 * <p>
 * 和 NeoForge 侧的转发对齐：发的是同一批事件类，取消语义相同（玩家渲染前、每只手渲染前可取消）。
 * Fabric 没有平台总线可以分档穿插，每个发出点 {@link CarianBus#post} 一次，五档在总线里依次跑完。
 * 每帧、每个实体都会走到的发出点先问 {@link CarianBus#hasListeners}，没人听就连事件对象都不建。
 * </p>
 * <p>
 * 世界渲染的三个阶段：
 * <ul>
 *     <li>{@code AFTER_CUTOUT_BLOCKS}：{@link WorldRenderEvents#BEFORE_ENTITIES}（剔除层画完、实体循环之前）；</li>
 *     <li>{@code AFTER_ENTITIES}：{@link WorldRenderEvents#AFTER_ENTITIES}（实体循环和四次 endBatch 之后，和 NeoForge 同位置）；</li>
 *     <li>{@code AFTER_TRANSLUCENT_BLOCKS}：不用 {@link WorldRenderEvents#AFTER_TRANSLUCENT}——它在粒子之后、
 *     没有缓冲、矩阵里也不含相机旋转，三处都和 NeoForge 不一样。改在半透明地形层内部、清渲染状态之前发
 *     （和 NeoForge 同一句，见 {@code MixinLevelRendererStages}）；那个点被别的模组整段改写、没发出来时，
 *     在 {@code renderLevel} 里那次半透明层调用返回后补发一次。</li>
 * </ul>
 * NeoForge 还会发别的阶段（本模组只当 {@code OTHER} 用来关火焰窗口）；Fabric 侧不发，窗口靠 {@code AFTER_ENTITIES}
 * 和半透明阶段关上。
 * </p>
 */
public final class FabricClientEvents {

    /**
     * 当前这次半透明层调用里，层内的主发出点发过没有。只在渲染线程读写。
     * {@link #beginTranslucentLayer()} 置 false，主发出点置 true，{@link #endTranslucentLayer()} 看它决定要不要补发。
     */
    private static boolean translucentStageFired;

    private FabricClientEvents() {
    }

    public static void init() {
        // 告诉总线这些客户端事件在 Fabric 上有人发，转发登记检查时不会当成没人发的监听
        CarianBus.provide(
                RenderLevelStageEvent.class,
                RenderPlayerEvent.Pre.class,
                RenderLivingEvent.Post.class,
                RenderHandEvent.class,
                ClientTickEvent.Post.class,
                ClientPlayerNetworkEvent.LoggingOut.class);

        WorldRenderEvents.BEFORE_ENTITIES.register(
                context -> postStage(RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS));
        WorldRenderEvents.AFTER_ENTITIES.register(
                context -> postStage(RenderLevelStageEvent.Stage.AFTER_ENTITIES));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (CarianBus.hasListeners(ClientTickPostEventImpl.class)) {
                CarianBus.post(new ClientTickPostEventImpl());
            }
        });

        // 断开回调由连接的断开处理触发，是不是一定在渲染线程上没核实；
        // 监听里会清客户端状态，不在渲染线程就转回渲染线程再发
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (client.isSameThread()) {
                postLoggingOut();
            } else {
                client.execute(FabricClientEvents::postLoggingOut);
            }
        });
    }

    private static void postLoggingOut() {
        if (CarianBus.hasListeners(ClientLoggingOutEventImpl.class)) {
            CarianBus.post(new ClientLoggingOutEventImpl());
        }
    }

    // ==================== 世界渲染阶段 ====================

    /** 相机和插值取法同 NeoForge：主相机、{@code Minecraft#getTimer()}。 */
    private static void postStage(RenderLevelStageEvent.Stage stage) {
        if (CarianBus.hasListeners(RenderLevelStageEventImpl.class)) {
            Minecraft mc = Minecraft.getInstance();
            CarianBus.post(new RenderLevelStageEventImpl(stage, mc.gameRenderer.getMainCamera(), mc.getTimer()));
        }
    }

    /** 层内主发出点：半透明地形层的顶点刚画完、渲染状态还没清。 */
    public static void onTranslucentLayerDrawn() {
        translucentStageFired = true;
        postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
    }

    /** {@code renderLevel} 调半透明层之前。 */
    public static void beginTranslucentLayer() {
        translucentStageFired = false;
    }

    /**
     * {@code renderLevel} 调半透明层返回之后：层内的主发出点没发（方法体被别的模组改写了），就在这里补发。
     * 补发时渲染状态已经清掉，画面可能和主发出点不完全一样，但特效至少还在。
     */
    public static void endTranslucentLayer() {
        if (!translucentStageFired) {
            translucentStageFired = true;
            postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
        }
    }

    // ==================== 玩家 / 生物 / 手部渲染 ====================

    /** 玩家模型渲染前（模型属性已设好）。返回 true 表示被取消，这个玩家这帧整个不画。 */
    public static boolean renderPlayerPre(Player player) {
        if (!CarianBus.hasListeners(RenderPlayerPreEventImpl.class)) {
            return false;
        }
        return CarianBus.post(new RenderPlayerPreEventImpl(player)).isCanceled();
    }

    /** 生物渲染的最后一句（模型、各层、名牌都画完之后）。 */
    public static void renderLivingPost(LivingEntity entity, float partialTick, PoseStack poseStack,
                                        MultiBufferSource bufferSource) {
        if (CarianBus.hasListeners(RenderLivingPostEventImpl.class)) {
            CarianBus.post(new RenderLivingPostEventImpl(entity, partialTick, poseStack, bufferSource));
        }
    }

    /** 第一人称每只手画之前各一次。返回 false 表示被取消，这只手（连同手上的物品）这帧不画。 */
    public static boolean renderHand(float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!CarianBus.hasListeners(RenderHandEventImpl.class)) {
            return true;
        }
        return !CarianBus.post(new RenderHandEventImpl(partialTick, poseStack, bufferSource)).isCanceled();
    }
}
