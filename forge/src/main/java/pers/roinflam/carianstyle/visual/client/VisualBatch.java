package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;

/**
 * 统一世界渲染批次（纯客户端）——把本模组全部世界自绘特效合并进<b>一次</b> GL 状态设置与<b>一次</b>顶点提交。
 * <p>
 * <b>解决的问题：</b>本模组在 {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS} 阶段有
 * 七个各自独立的渲染器（猩红腐败雾 / 冻伤冰雾 / 出血飙血 / 黄金树祝福 / 重力压制 / 光环法阵 / 定点 AOE），
 * 优化前<b>每个渲染器每帧都要完整跑一遍</b>：
 * <pre>
 *     enableBlend → defaultBlendFunc → disableCull → depthMask(false) → enableDepthTest → setShader
 *         → builder.begin(...) → 写顶点 → BufferUploader.drawWithShader(builder.end())
 *         → depthMask(true) → enableCull → disableBlend
 * </pre>
 * 也就是每帧 7 次 GL 状态切换往返 + 7 次 draw call，而七者的 GL 状态与顶点格式<b>完全相同</b>
 * （普通 alpha 混合、关剔除、关深度写入、{@code POSITION_COLOR} + {@code getPositionColorShader}），
 * 纯属重复开销。
 * </p>
 * <p>
 * <b>做法：</b>本类在同一事件上挂两个监听：
 * <ul>
 *     <li>{@link EventPriority#HIGHEST}（{@link #onBatchBegin}）——开启共享顶点缓冲；</li>
 *     <li>{@link EventPriority#LOWEST}（{@link #onBatchEnd}）——设置 GL 状态、一次性提交、恢复 GL 状态。</li>
 * </ul>
 * 各渲染器保持自己的 {@code @SubscribeEvent}（默认 {@link EventPriority#NORMAL} 优先级，
 * 天然夹在两者之间），只需去掉 GL 样板、改用 {@link #builder()} / {@link #matrix()} 取共享缓冲即可。
 * </p>
 * <p>
 * <b>为什么不做成「统一调度器逐个回调渲染器」：</b>那样会把各渲染器的绘制先后顺序固定成调度器里写死的次序，
 * 而当前次序是由 Forge 的事件分发决定的。七者都是关深度写入的半透明叠加，绘制顺序会影响重叠区域的
 * 混合结果。保留各自的事件订阅，顺序与优化前<b>完全一致</b>，从而做到逐像素不变；同时也支持逐个渲染器
 * 增量迁移——尚未迁移的渲染器继续用自己的 GL 批次，与本批次并存、互不干扰。
 * </p>
 * <p>
 * <b>关于 GL 状态设置时机：</b>顶点写入是纯 CPU 行为，真正需要 GL 状态正确的是提交那一刻。
 * 因此本类把<b>全部</b> GL 状态设置放在 {@link #onBatchEnd} 的提交之前，而不是 {@link #onBatchBegin}。
 * 这样即便其它模组的渲染器在两者之间改动了着色器或混合模式，也不会影响本模组的提交结果。
 * </p>
 * <p>
 * <b>关于顶点缓冲：</b>刻意<b>不</b>使用 {@code Tesselator.getInstance()} 的全局单例缓冲，而是持有一个
 * 私有的 {@link BufferBuilder}。原因是本批次会在整个事件分发期间保持「开启」状态，若占用全局单例，
 * 其它模组在此期间调用 {@code Tesselator} 会撞上「Already building」而崩溃——这在重度模组整合包
 * （尤其 Mohist 混合端）上是实打实的风险。私有缓冲彻底规避该问题。
 * {@link BufferBuilder} 容量不足时会自动扩容，初始 2MB 只是为了避免早期反复扩容。
 * </p>
 * <p>
 * <b>相机参数共享：</b>五个实体类特效渲染器每帧都要各自取一遍相机位置、朝向向量、变换矩阵与帧间插值系数，
 * 现统一由本类在 {@link #onBatchBegin} 计算一次并暴露出去，避免重复计算与重复的
 * {@code getUpVector()} / {@code getLeftVector()} 调用。
 * </p>
 * <p>
 * <b>细节层级：</b>合并 draw call 与共享查询之后，客户端剩下的瓶颈是<b>顶点量</b>。
 * {@link VisualLod} 负责按距离与同屏拥挤度削减元素数量，其帧级状态由本类在
 * {@link #onBatchBegin} 驱动（{@link VisualLod#beginFrame()}），各渲染器只需在循环内
 * 取一次细节系数即可。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class VisualBatch {

    /**
     * 共享顶点缓冲初始容量（字节）。
     * <p>{@code POSITION_COLOR} 每顶点 16 字节，2MB 约合 13 万顶点，足够覆盖同屏全部特效；
     * 不足时 {@link BufferBuilder} 会自动扩容，此值仅用于避免早期反复扩容。</p>
     */
    private static final int BUFFER_CAPACITY = 2097152;

    /**
     * 私有共享顶点缓冲。
     * <p>不用 {@code Tesselator} 全局单例，避免与其它模组抢占（详见类注释）。</p>
     */
    private static final BufferBuilder BUILDER = new BufferBuilder(BUFFER_CAPACITY);

    /** 本帧批次是否已开启（未开启时 {@link #builder()} 返回 null，渲染器应直接跳过） */
    private static boolean building = false;

    /** 帧序号：每开启一次批次自增，供 {@link SharedEntityQuery} 判断缓存是否属于本帧 */
    private static int frameId = 0;

    /** 本帧变换矩阵（世界渲染 PoseStack 顶层） */
    @Nullable
    private static Matrix4f matrix;

    /** 本帧相机世界坐标 */
    @Nullable
    private static Vec3 cameraPosition;

    /** 本帧帧间插值系数 */
    private static float partialTick;

    // ===== 本帧相机朝向向量（billboard 用，右向量由左向量取反得到）=====
    private static float rightX;
    private static float rightY;
    private static float rightZ;
    private static float upX;
    private static float upY;
    private static float upZ;

    private VisualBatch() {
    }

    /**
     * 批次开启（HIGHEST，早于全部渲染器）：记录本帧相机参数并开启共享顶点缓冲。
     * <p>此处<b>不</b>设置任何 GL 状态，全部推迟到 {@link #onBatchEnd} 提交前（原因见类注释）。</p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBatchBegin(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            building = false;
            return;
        }

        frameId++;
        // 用上一帧的特效实例数算出本帧拥挤系数，并复位计数器（详见 VisualLod 类注释）
        VisualLod.beginFrame();

        Camera camera = event.getCamera();
        cameraPosition = camera.getPosition();
        partialTick = event.getPartialTick();
        matrix = event.getPoseStack().last().pose();

        // 相机朝向：右向量 = -左向量（与各渲染器优化前的取法完全一致）
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        rightX = -left.x();
        rightY = -left.y();
        rightZ = -left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();

        BUILDER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // 空缓冲兜底：无条件追加一个零面积、全透明的退化三角形，保证 begin/end 之间永不为空
        // （原先每个渲染器各自做一次，现统一在此做一次）
        emitDegenerateTriangle(BUILDER, matrix);
        building = true;
    }

    /**
     * 批次提交（LOWEST，晚于全部渲染器）：设置 GL 状态、一次性提交全部顶点、恢复 GL 状态。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBatchEnd(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        if (!building) {
            return;
        }
        building = false;

        // GL 状态：与优化前每个渲染器各自设置的完全一致（普通 alpha 混合、关剔除、关深度写入、保留深度测试）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferUploader.drawWithShader(BUILDER.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /**
     * 追加一个零面积、全透明的退化三角形，保证缓冲永不为空。
     *
     * @param b 顶点缓冲
     * @param m 变换矩阵
     */
    private static void emitDegenerateTriangle(BufferBuilder b, Matrix4f m) {
        for (int i = 0; i < 3; i++) {
            b.vertex(m, 0f, 0f, 0f).color(0f, 0f, 0f, 0f).endVertex();
        }
    }

    /**
     * 取本帧共享顶点缓冲。
     * <p>渲染器应在最开头调用并判空：返回 null 表示本帧批次未开启（如世界尚未加载 /
     * 当前不是目标渲染阶段），此时应直接返回、不做任何绘制。</p>
     *
     * @return 共享顶点缓冲；批次未开启时为 null
     */
    @Nullable
    public static BufferBuilder builder() {
        return building ? BUILDER : null;
    }

    /**
     * @return 本帧变换矩阵；批次未开启时为 null
     */
    @Nullable
    public static Matrix4f matrix() {
        return matrix;
    }

    /**
     * @return 本帧相机世界坐标；批次未开启时为 null
     */
    @Nullable
    public static Vec3 cameraPosition() {
        return cameraPosition;
    }

    /**
     * @return 本帧帧间插值系数
     */
    public static float partialTick() {
        return partialTick;
    }

    /**
     * @return 帧序号（每帧自增，供帧级缓存判断有效性）
     */
    public static int frameId() {
        return frameId;
    }

    /**
     * @return 相机右向量 X（billboard 用）
     */
    public static float rightX() {
        return rightX;
    }

    /**
     * @return 相机右向量 Y（billboard 用）
     */
    public static float rightY() {
        return rightY;
    }

    /**
     * @return 相机右向量 Z（billboard 用）
     */
    public static float rightZ() {
        return rightZ;
    }

    /**
     * @return 相机上向量 X（billboard 用）
     */
    public static float upX() {
        return upX;
    }

    /**
     * @return 相机上向量 Y（billboard 用）
     */
    public static float upY() {
        return upY;
    }

    /**
     * @return 相机上向量 Z（billboard 用）
     */
    public static float upZ() {
        return upZ;
    }
}
