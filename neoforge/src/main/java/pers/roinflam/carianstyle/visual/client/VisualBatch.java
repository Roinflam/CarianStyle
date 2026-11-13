package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import net.neoforged.fml.common.EventBusSubscriber;

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
 * 私有的字节缓冲（{@link ByteBufferBuilder}）。原因是本批次会在整个事件分发期间保持「开启」状态，若占用全局单例，
 * 其它模组在此期间调用 {@code Tesselator} 会撞上「Already building」而崩溃——这在重度模组整合包
 * （尤其 Mohist 系混合端）上是实打实的风险。私有缓冲彻底规避该问题。
 * {@link ByteBufferBuilder} 容量不足时会自动扩容，初始 2MB 只是为了避免早期反复扩容。
 * </p>
 * <p>
 * <b>1.21.1 的写法：</b>{@link BufferBuilder} 不再自带内存，只能套在一个 {@link ByteBufferBuilder} 上、
 * 一次性使用（{@code build()} 之后作废）。所以字节缓冲是常驻的静态对象，{@link BufferBuilder}
 * 每帧开批时新建一个；提交用 {@code build()} 得到 {@link MeshData}，交给
 * {@link BufferUploader#drawWithShader} 画完后由它负责释放。
 * </p>
 * <p>
 * <b>变换矩阵为什么是单位阵（1.21.1）：</b>1.20.1 取的是事件里世界渲染 PoseStack 的顶层（带相机旋转）。
 * 1.21.1 的 {@code AFTER_TRANSLUCENT_BLOCKS} 是按区块渲染层发出的阶段，事件里的 PoseStack 恒为
 * null；而原版在画半透明层之前已经把相机旋转乘进了 {@code RenderSystem} 的模型视图栈，
 * {@link BufferUploader#drawWithShader} 提交时用的正是那个矩阵。顶点这边再乘一次相机旋转就会转两遍，
 * 所以 {@link #matrix()} 给单位阵；相机平移照旧由各渲染器自己减相机坐标，渲染器一行不用改。
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
 * @author RoinFlam
 * @version 1.1
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class VisualBatch {

    /**
     * 共享顶点缓冲初始容量（字节）。
     * <p>{@code POSITION_COLOR} 每顶点 16 字节，2MB 约合 13 万顶点，足够覆盖同屏全部特效；
     * 不足时 {@link ByteBufferBuilder} 会自动扩容，此值仅用于避免早期反复扩容。</p>
     */
    private static final int BUFFER_CAPACITY = 2097152;

    /**
     * 私有共享字节缓冲（常驻，整个客户端生命周期只分配一次）。
     * <p>不用 {@code Tesselator} 全局单例，避免与其它模组抢占（详见类注释）。
     * 第一次开批时才在渲染线程上分配本地内存，不在类初始化时提前申请。</p>
     */
    @Nullable
    private static ByteBufferBuilder bytes;

    /** 本帧的顶点写入器（开批时新建，提交后作废；未开批时为 null） */
    @Nullable
    private static BufferBuilder builder;

    /** 本帧批次是否已开启（未开启时 {@link #builder()} 返回 null，渲染器应直接跳过） */
    private static boolean building = false;

    /** 帧序号：每开启一次批次自增，供 {@link SharedEntityQuery} 判断缓存是否属于本帧 */
    private static int frameId = 0;

    /**
     * 本帧变换矩阵：恒为单位阵（原因见类注释「变换矩阵为什么是单位阵」）。
     * <p>每帧开批时重置一次，防止哪个渲染器误改了它而影响下一帧。</p>
     */
    private static final Matrix4f MATRIX = new Matrix4f();

    /** 本帧变换矩阵（批次未开启时为 null） */
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
        // 1.21.1 的世界渲染阶段事件给的是 DeltaTracker，取法和原版 LevelRenderer.renderLevel 一致
        partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 这个阶段事件里的 PoseStack 为 null，相机旋转已在 RenderSystem 的模型视图矩阵里（见类注释）
        matrix = MATRIX.identity();

        // 相机朝向：右向量 = -左向量（与各渲染器优化前的取法完全一致）
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();
        rightX = -left.x();
        rightY = -left.y();
        rightZ = -left.z();
        upX = up.x();
        upY = up.y();
        upZ = up.z();

        if (bytes == null) {
            bytes = new ByteBufferBuilder(BUFFER_CAPACITY);
        }
        discardUnfinishedFrame();
        builder = new BufferBuilder(bytes, VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        // 空缓冲兜底：无条件追加一个零面积、全透明的退化三角形，保证 begin/end 之间永不为空
        // （原先每个渲染器各自做一次，现统一在此做一次）
        emitDegenerateTriangle(builder, matrix);
        building = true;
    }

    /**
     * 丢掉上一帧没提交的残留顶点。
     * <p>
     * <b>问题：</b>{@link ByteBufferBuilder#build()} 交出的是「上次交出结果之后写进来的全部字节」。
     * 若某一帧有渲染器在两次监听之间抛了异常，{@link #onBatchEnd} 不会执行，那一帧写了一半的顶点
     * 就留在字节缓冲里；下一帧新建的 {@link BufferBuilder} 接着往后写，提交时这段残留会被当成本帧的
     * 开头一起交出去，和本帧的顶点数对不上，画出来就是乱的。
     * </p>
     * <p>
     * <b>做法：</b>上一帧还处于开启状态时，把那个写入器收尾成网格再立刻释放——释放最后一个结果时
     * 字节缓冲会把写入位置归零；之后再 {@link ByteBufferBuilder#discard()} 一次，兜住「交出了结果却没释放」
     * 的情况（没有未释放结果时它什么也不做）。只调 {@code discard()} 不够：它只清已交出的结果，
     * 写了却没交出的字节它不管。
     * </p>
     * <p>1.20.1 遇到同样的情况会在下一帧 {@code begin} 时抛「Already building」，每帧都崩；这里改成静默恢复。</p>
     */
    private static void discardUnfinishedFrame() {
        // 正常提交后 builder 已置空；还不为空就是上一次开了批却没走到提交
        if (builder != null) {
            building = false;
            MeshData stale = builder.build();
            if (stale != null) {
                stale.close();
            }
        }
        builder = null;
        if (bytes != null) {
            bytes.discard();
        }
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
        if (!building || builder == null) {
            return;
        }
        building = false;
        BufferBuilder current = builder;
        builder = null;
        // 有退化三角形垫底，正常不会是空的；没有顶点时 build() 返回 null，照样跳过提交
        MeshData mesh = current.build();
        if (mesh == null) {
            return;
        }

        // GL 状态：与优化前每个渲染器各自设置的完全一致（普通 alpha 混合、关剔除、关深度写入、保留深度测试）
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // drawWithShader 内部上传完会关闭 MeshData，不用自己关
        BufferUploader.drawWithShader(mesh);

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
            b.addVertex(m, 0f, 0f, 0f).setColor(0f, 0f, 0f, 0f);
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
        return building ? builder : null;
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
