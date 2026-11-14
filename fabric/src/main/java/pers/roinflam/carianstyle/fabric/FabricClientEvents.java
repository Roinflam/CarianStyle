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
 *     在 {@code renderLevel} 里那次半透明层调用返回后补发一次。两处都没发时世界渲染结束打一次警告。</li>
 *     <li>{@code OTHER}：{@link WorldRenderEvents#START}（每帧世界渲染开头）。NeoForge 每帧还会发天空之后等别的阶段，
 *     本模组只当 {@code OTHER} 用来兜底关火焰窗口，这里每帧补一次，某帧没正常关的窗口下一帧开头也会关上。</li>
 * </ul>
 * </p>
 * <p>
 * 断开事件不用 Fabric API 的连接断开回调，改由 {@code MixinMinecraftDisconnect} 在 {@code Minecraft#disconnect} 里同步发，
 * 原因见那个类。
 * </p>
 */
public final class FabricClientEvents {

    /**
     * 当前这次半透明层调用里，层内的主发出点发过没有。只在渲染线程读写。
     * {@link #beginTranslucentLayer()} 置 false，主发出点置 true，{@link #endTranslucentLayer()} 看它决定要不要补发。
     */
    private static boolean translucentStageFired;

    /** 是不是正处在 {@code renderLevel} 包住的那次半透明层调用里。只在渲染线程读写。 */
    private static boolean inWrappedTranslucentLayer;

    /** 这一帧世界渲染里半透明阶段发过没有（主发出点或回退点都算）。只在渲染线程读写。 */
    private static boolean translucentReachedThisFrame;

    /** 回退点第一次补发时打过日志没有，整局只打一次（看日志就分得出这局走的是主发出点还是回退点）。 */
    private static boolean fallbackUsedLogged;

    /** 「半透明阶段没发出」的警告打过没有，整局只打一次。 */
    private static boolean translucentMissWarned;

    private static final Logger LOGGER = LogUtils.getLogger();

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

        // 每帧世界渲染开头：发一次 OTHER，顶替 NeoForge 每帧都会到的那些别的阶段（火焰窗口靠它兜底关上），
        // 顺带清掉本帧「半透明阶段到没到」的记录
        WorldRenderEvents.START.register(context -> {
            translucentReachedThisFrame = false;
            postStage(RenderLevelStageEvent.Stage.OTHER);
        });
        WorldRenderEvents.END.register(context -> checkTranslucentReached());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (CarianBus.hasListeners(ClientTickPostEventImpl.class)) {
                CarianBus.post(new ClientTickPostEventImpl());
            }
        });
    }

    /** {@code Minecraft#disconnect} 里，见 {@code MixinMinecraftDisconnect}。在渲染线程上同步发。 */
    public static void onDisconnect() {
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

    /**
     * 层内主发出点：半透明地形层的顶点刚画完、渲染状态还没清。
     * 在 {@code renderLevel} 包住的那次半透明层调用里已经发过就跳过：原版方法体里只有一处匹配，
     * 但被别的模组整段改写后可能匹配到好几处，每处都发会让特效批次在同一帧开收好几遍。
     * 别的模组在 {@code renderLevel} 外直接调半透明层时不经包装，每次调用照常发（和 NeoForge 一样）。
     */
    public static void onTranslucentLayerDrawn() {
        if (inWrappedTranslucentLayer) {
            if (translucentStageFired) {
                return;
            }
            translucentStageFired = true;
        }
        translucentReachedThisFrame = true;
        postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
    }

    /** {@code renderLevel} 调半透明层之前。 */
    public static void beginTranslucentLayer() {
        inWrappedTranslucentLayer = true;
        translucentStageFired = false;
    }

    /**
     * {@code renderLevel} 调半透明层返回之后：层内的主发出点没发（方法体被别的模组改写了），就在这里补发。
     * 补发时渲染状态已经清掉，画面可能和主发出点不完全一样，但特效至少还在。
     */
    public static void endTranslucentLayer() {
        inWrappedTranslucentLayer = false;
        if (!translucentStageFired) {
            translucentStageFired = true;
            translucentReachedThisFrame = true;
            if (!fallbackUsedLogged) {
                fallbackUsedLogged = true;
                LOGGER.info("[CarianStyle] 半透明方块层内的注入点没有挂上（LevelRenderer 可能被别的渲染模组改写过），"
                        + "半透明阶段改在这一层画完之后发出。");
            }
            postStage(RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS);
        }
    }

    /**
     * 世界渲染结束时看这一帧半透明阶段到没到。主发出点和回退点都允许挂不上（别的渲染模组改掉了这些位置），
     * 两处都没发时游戏照常跑、只是范围特效画不出来；这里打一行警告说明原因，整局只打一次。
     */
    private static void checkTranslucentReached() {
        if (translucentReachedThisFrame || translucentMissWarned) {
            return;
        }
        translucentMissWarned = true;
        LOGGER.warn("[CarianStyle] 这一帧世界渲染结束时，半透明方块之后的渲染阶段事件没有发出"
                + "（LevelRenderer 里的注入点可能被别的渲染模组改掉了），模组的范围特效在本次游戏中可能不显示。");
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
