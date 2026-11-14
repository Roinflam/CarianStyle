package pers.roinflam.carianstyle.fabric;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
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
 * 客户端 / 渲染相关的 Fabric API 回调和 {@code carianstyle.fabric.client.mixins.json} 里的 mixin 都经这里发到自带总线。
 * 只由客户端入口调用；mixin 只取参数、调这里的静态方法。
 * <p>
 * 世界渲染阶段：{@code AFTER_CUTOUT_BLOCKS} 接 {@link WorldRenderEvents#BEFORE_ENTITIES}，{@code AFTER_ENTITIES} 接
 * {@link WorldRenderEvents#AFTER_ENTITIES}，{@code OTHER} 接 {@link WorldRenderEvents#START}（每帧一次，给火焰窗口兜底关窗）。
 * {@code AFTER_TRANSLUCENT_BLOCKS} 不用 {@link WorldRenderEvents#AFTER_TRANSLUCENT}（在粒子之后，和 Forge 不是一个位置），
 * 改在半透明地形层里清渲染状态之前发，和 Forge 同一句，见 {@code MixinLevelRendererStages}。
 * </p>
 */
public final class FabricClientEvents {

    // 下面几个只在渲染线程读写
    private static boolean translucentStageFired;
    private static boolean inWrappedTranslucentLayer;
    private static boolean translucentReachedThisFrame;
    private static boolean fallbackUsedLogged;
    private static boolean translucentMissWarned;

    // renderLevel 的第 2 个参数，和 Forge 渲染阶段事件的 getPartialTick() 是同一个值（暂停时是暂停那一刻的值）
    private static float levelPartialTick;

    private static final Logger LOGGER = LogUtils.getLogger();

    private FabricClientEvents() {
    }

    public static void init() {
        CarianBus.provide(
                RenderLevelStageEvent.class,
                RenderPlayerEvent.Pre.class,
                RenderLivingEvent.Post.class,
                RenderHandEvent.class,
                ClientTickEvent.Post.class,
                ClientPlayerNetworkEvent.LoggingOut.class);

        WorldRenderEvents.BEFORE_ENTITIES.register(
                context -> postStage(RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS, context.matrixStack()));
        WorldRenderEvents.AFTER_ENTITIES.register(
                context -> postStage(RenderLevelStageEvent.Stage.AFTER_ENTITIES, context.matrixStack()));
        WorldRenderEvents.START.register(context -> {
            translucentReachedThisFrame = false;
            postStage(RenderLevelStageEvent.Stage.OTHER, context.matrixStack());
        });
        WorldRenderEvents.END.register(context -> checkTranslucentReached());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (CarianBus.hasListeners(ClientTickPostEventImpl.class)) {
                CarianBus.post(new ClientTickPostEventImpl());
            }
        });
    }

    public static void onDisconnect() {
        if (CarianBus.hasListeners(ClientLoggingOutEventImpl.class)) {
            CarianBus.post(new ClientLoggingOutEventImpl());
        }
    }

    public static void onRenderLevelStart(float partialTick) {
        levelPartialTick = partialTick;
    }

    private static void postStage(RenderLevelStageEvent.Stage stage, PoseStack poseStack) {
        if (CarianBus.hasListeners(RenderLevelStageEventImpl.class)) {
            Minecraft mc = Minecraft.getInstance();
            CarianBus.post(new RenderLevelStageEventImpl(stage, mc.gameRenderer.getMainCamera(), levelPartialTick,
                    poseStack));
        }
    }

    /**
     * 层内主发出点。被包住的那次半透明层调用里只发第一次：方法体被别的模组改写后可能匹配到好几处，
     * 每处都发会让特效批次同一帧开收好几遍。不经包装的直接调用每次照发，同 Forge。
     */
    public static void onTranslucentLayerDrawn(PoseStack poseStack) {
        if (inWrappedTranslucentLayer) {
            if (translucentStageFired) {
                return;
            }
            translucentStageFired = true;
        }
        translucentReachedThisFrame = true;
        postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, poseStack);
    }

    public static void beginTranslucentLayer() {
        inWrappedTranslucentLayer = true;
        translucentStageFired = false;
    }

    /** 半透明层画完返回后，层内主发出点没发就补发一次（渲染状态已清，画面可能和主发出点略有不同）。 */
    public static void endTranslucentLayer(PoseStack poseStack) {
        inWrappedTranslucentLayer = false;
        if (!translucentStageFired) {
            translucentStageFired = true;
            translucentReachedThisFrame = true;
            if (!fallbackUsedLogged) {
                fallbackUsedLogged = true;
                LOGGER.info("[CarianStyle] 半透明方块层内的注入点没有挂上（LevelRenderer 可能被别的渲染模组改写过），"
                        + "半透明阶段改在这一层画完之后发出。");
            }
            postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS, poseStack);
        }
    }

    private static void checkTranslucentReached() {
        if (translucentReachedThisFrame || translucentMissWarned) {
            return;
        }
        translucentMissWarned = true;
        LOGGER.warn("[CarianStyle] 这一帧世界渲染结束时，半透明方块之后的渲染阶段事件没有发出"
                + "（LevelRenderer 里的注入点可能被别的渲染模组改掉了），模组的范围特效在本次游戏中可能不显示。");
    }

    /** 返回 true 表示被取消，这个玩家这帧整个不画。 */
    public static boolean renderPlayerPre(Player player) {
        if (!CarianBus.hasListeners(RenderPlayerPreEventImpl.class)) {
            return false;
        }
        return CarianBus.post(new RenderPlayerPreEventImpl(player)).isCanceled();
    }

    public static void renderLivingPost(LivingEntity entity, float partialTick, PoseStack poseStack,
                                        MultiBufferSource bufferSource) {
        if (CarianBus.hasListeners(RenderLivingPostEventImpl.class)) {
            CarianBus.post(new RenderLivingPostEventImpl(entity, partialTick, poseStack, bufferSource));
        }
    }

    /** 返回 false 表示被取消，这只手连同手上的物品这帧不画。 */
    public static boolean renderHand(float partialTick, PoseStack poseStack, MultiBufferSource bufferSource) {
        if (!CarianBus.hasListeners(RenderHandEventImpl.class)) {
            return true;
        }
        return !CarianBus.post(new RenderHandEventImpl(partialTick, poseStack, bufferSource)).isCanceled();
    }
}
