package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import pers.roinflam.carianstyle.mc112.math.Matrix4f;
import pers.roinflam.carianstyle.mc112.render.BufferBuilder;
import pers.roinflam.carianstyle.mc112.render.GlPresets;
import pers.roinflam.carianstyle.mc112.render.GlStateSnapshot;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nullable;

/**
 * 统一世界渲染批次（纯客户端）——把本模组全部世界自绘特效合并进<b>一次</b> GL 状态设置与<b>一次</b>顶点提交。
 * <p>
 * <b>解决的问题：</b>本模组有七个以上各自独立的世界渲染器（猩红腐败雾 / 冻伤冰雾 / 出血飙血 / 黄金树祝福 /
 * 重力压制 / 光环法阵 / 定点 AOE……），如果每个渲染器每帧都完整跑一遍
 * 「开混合 → 设混合函数 → 关剔除 → 关深度写 → 开批 → 写顶点 → 提交 → 还原」，
 * 就是每帧 N 次 GL 状态切换往返 + N 次 draw call，而它们的 GL 状态与顶点格式<b>完全相同</b>
 * （普通 alpha 混合、关剔除、关深度写入、{@code POSITION_COLOR}），纯属重复开销。
 * </p>
 * <p>
 * <b>做法：</b>本类在同一事件上挂两个监听：
 * <ul>
 *     <li>{@link EventPriority#HIGHEST}（{@link #onBatchBegin}）——开启共享顶点缓冲；</li>
 *     <li>{@link EventPriority#LOWEST}（{@link #onBatchEnd}）——设置 GL 状态、一次性提交、恢复 GL 状态。</li>
 * </ul>
 * 各渲染器保持自己的 {@code @SubscribeEvent}（默认 {@link EventPriority#NORMAL} 优先级，
 * 天然夹在两者之间），只需用 {@link #builder()} / {@link #matrix()} 取共享缓冲即可。
 * </p>
 * <p>
 * 1.12.2 版：挂在 {@link RenderWorldLastEvent} 上（1.20.1 是 {@code RenderLevelStageEvent} 的
 * {@code AFTER_TRANSLUCENT_BLOCKS} 阶段，所以那边每个监听开头都要判断阶段，这边不用）。
 * 这个事件在半透明方块和雨雪之后才发，特效会画在雨前面、水面也挡不住它们（水不写深度），
 * 这是 1.12.2 渲染顺序决定的，和 1.20.1 看起来略有差别。
 * </p>
 * <p>
 * <b>为什么不做成「统一调度器逐个回调渲染器」：</b>那样会把各渲染器的绘制先后顺序固定成调度器里写死的次序，
 * 而当前次序是由 Forge 的事件分发决定的。它们都是关深度写入的半透明叠加，绘制顺序会影响重叠区域的
 * 混合结果。保留各自的事件订阅，顺序就和各渲染器单独画时<b>完全一致</b>。
 * </p>
 * <p>
 * <b>关于 GL 状态设置时机：</b>顶点写入是纯 CPU 行为，真正需要 GL 状态正确的是提交那一刻。
 * 因此本类把<b>全部</b> GL 状态设置放在 {@link #onBatchEnd} 的提交之前，而不是 {@link #onBatchBegin}；
 * 开批只往内存缓冲写，不碰任何 GL 状态。这样即便其它模组的渲染器在两者之间改动了混合模式，
 * 也不会影响本模组的提交结果。
 * </p>
 * <p>
 * 1.12.2 版：进出状态走 {@link GlPresets#WORLD_POSITION_COLOR}：提交前 {@code enter()} 先记下当前真实状态再设预设，
 * 提交后 {@link GlStateSnapshot#restore()} 只把预设改过的项改回记下的值。别的模组也订阅这个事件，
 * 进来时的状态不能当成定值；全程走 {@code GlStateManager}（不用 {@code glPushAttrib}，那会让
 * {@code GlStateManager} 的缓存和真实状态错位），也不 {@code pushMatrix}。
 * </p>
 * <p>
 * <b>关于顶点缓冲：</b>刻意<b>不</b>使用 {@code Tessellator.getInstance()} 的全局单例缓冲，而是持有一个
 * 私有的缓冲。原因是本批次会在整个事件分发期间保持「开启」状态，若占用全局单例，
 * 其它模组在此期间调用 {@code Tessellator} 会撞上「Already building」而崩溃——这在重度模组整合包
 * （尤其 Mohist 系混合端）上是实打实的风险。私有缓冲彻底规避该问题。
 * 缓冲容量不足时会自动扩容，初始 2MB 只是为了避免早期反复扩容。
 * </p>
 * <p>
 * <b>相机参数共享：</b>各实体类特效渲染器每帧都要取相机位置、朝向向量、变换矩阵与帧间插值系数，
 * 统一由本类在 {@link #onBatchBegin} 计算一次并暴露出去。
 * </p>
 * <p>
 * 1.12.2 版的相机：
 * </p>
 * <ul>
 *     <li>{@link #cameraPosition()} 和 1.20.1 一样是<b>真正的相机位置</b>（眼睛；第三人称是拉远后的位置），
 *         = 渲染原点（{@link RenderManager#viewerPosX} 等，视角实体的插值位置）+
 *         {@link ActiveRenderInfo#getCameraPosition()}（相机相对渲染原点的偏移，
 *         {@code EntityRenderer} 每帧设好相机变换后算出来的）。</li>
 *     <li>{@link RenderWorldLastEvent} 里模型视图矩阵已经带着相机旋转（第三人称还带着拉远的那段平移），
 *         坐标原点是渲染原点，所以 {@link #matrix()} 恒为单位矩阵。各渲染器照 1.20.1 写
 *         「世界坐标 − {@link #cameraPosition()}」得到相对相机的坐标，缓冲上的
 *         {@code setTranslation(相机偏移)} 再把它挪回相对渲染原点，正好对上模型视图矩阵。</li>
 *     <li>公告板方向取自 {@link ActiveRenderInfo} 的五个旋转值（字节码：{@code rotationX = cos(yaw)}、
 *         {@code rotationZ = sin(yaw)}、{@code rotationYZ = -rotationZ·sin(pitch)}、
 *         {@code rotationXY = rotationX·sin(pitch)}、{@code rotationXZ = cos(pitch)}，正面第三人称时前四个整体取反、
 *         正好对应相机转了 180° 的朝向）：右 = {@code (-rotationX, 0, -rotationZ)}，
 *         上 = {@code (rotationYZ, rotationXZ, rotationXY)}，和 1.20.1 的「右 = −左」「上」同义。
 *         第一次开批时自检一次长度和正交性，偏差超过 {@value #BILLBOARD_EPSILON} 就打一行日志
 *         （别的模组改了相机朝向的计算时，这里最先露馅）。</li>
 * </ul>
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
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class VisualBatch {

    /**
     * 共享顶点缓冲初始容量（按 int 计）。
     * <p>1.12.2 的缓冲构造参数是 int 个数，内部分配 ×4 字节：524288 = 2MB，和 1.20.1 的 2097152 字节一样。
     * {@code POSITION_COLOR} 每顶点 16 字节，约合 13 万顶点，足够覆盖同屏全部特效；不足时会自动扩容。</p>
     */
    private static final int BUFFER_CAPACITY = 524288;

    /** 公告板向量自检的容差：长度和 1 的差、两向量点积，超过它就打日志 */
    private static final float BILLBOARD_EPSILON = 1.0e-3F;

    /**
     * 私有共享顶点缓冲（MC 的缓冲本体）。
     * <p>不用 {@code Tessellator} 全局单例，避免与其它模组抢占（详见类注释）。</p>
     */
    private static final net.minecraft.client.renderer.BufferBuilder RAW =
            new net.minecraft.client.renderer.BufferBuilder(BUFFER_CAPACITY);

    /** 给渲染器用的写顶点包装，形状照 1.20.1 的 {@code BufferBuilder} */
    private static final BufferBuilder BUILDER = new BufferBuilder(RAW);

    /** 常驻的提交器（无状态，复用一个即可） */
    private static final WorldVertexBufferUploader UPLOADER = new WorldVertexBufferUploader();

    /** 本帧批次是否已开启（未开启时 {@link #builder()} 返回 null，渲染器应直接跳过） */
    private static boolean building = false;

    /** 公告板向量是否已经自检过 */
    private static boolean billboardChecked = false;

    /** 帧序号：每开启一次批次自增，供 {@link SharedEntityQuery} 判断缓存是否属于本帧 */
    private static int frameId = 0;

    /** 本帧相机世界坐标 */
    @Nullable
    private static Vec3d cameraPosition;

    /** 本帧帧间插值系数 */
    private static float partialTick;

    // ===== 本帧相机朝向向量（billboard 用）=====
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
     * @param event 世界渲染末尾事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBatchBegin(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (building) {
            // 上一批没走到收批（中途有渲染器抛了异常）：丢掉残留顶点，免得 begin 撞上「Already building」
            RAW.finishDrawing();
            RAW.reset();
            RAW.setTranslation(0, 0, 0);
            building = false;
        }
        if (mc.world == null) {
            return;
        }

        frameId++;
        // 用上一帧的特效实例数算出本帧拥挤系数，并复位计数器（详见 VisualLod 类注释）
        VisualLod.beginFrame();

        partialTick = event.getPartialTicks();

        // 相机偏移（相对渲染原点）+ 渲染原点 = 真正的相机位置
        Vec3d offset = ActiveRenderInfo.getCameraPosition();
        RenderManager rm = mc.getRenderManager();
        cameraPosition = new Vec3d(rm.viewerPosX + offset.x, rm.viewerPosY + offset.y, rm.viewerPosZ + offset.z);

        // 相机朝向（推导见类注释）
        rightX = -ActiveRenderInfo.getRotationX();
        rightY = 0.0F;
        rightZ = -ActiveRenderInfo.getRotationZ();
        upX = ActiveRenderInfo.getRotationYZ();
        upY = ActiveRenderInfo.getRotationXZ();
        upZ = ActiveRenderInfo.getRotationXY();
        if (!billboardChecked) {
            billboardChecked = true;
            checkBillboardVectors();
        }

        RAW.begin(GL11.GL_TRIANGLES, DefaultVertexFormats.POSITION_COLOR);
        // 渲染器写的是「相对相机」的坐标，加上相机偏移就成了「相对渲染原点」，对上模型视图矩阵
        RAW.setTranslation(offset.x, offset.y, offset.z);
        // 空缓冲兜底：无条件追加一个零面积、全透明的退化三角形，保证 begin/end 之间永不为空
        emitDegenerateTriangle(BUILDER, Matrix4f.IDENTITY);
        building = true;
    }

    /**
     * 批次提交（LOWEST，晚于全部渲染器）：设置 GL 状态、一次性提交全部顶点、恢复 GL 状态。
     *
     * @param event 世界渲染末尾事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBatchEnd(RenderWorldLastEvent event) {
        if (!building) {
            return;
        }
        building = false;

        RAW.finishDrawing();
        // GL 状态只在提交这一小段里改：混合开、关剔除、关贴图 / 光照 / alpha 测试 / 雾 / 光照贴图、
        // 平滑着色、深度测试开（LEQUAL）、深度写关、颜色白（预设内容见 GlPresets）
        GlStateSnapshot snapshot = GlPresets.WORLD_POSITION_COLOR.enter();
        try {
            // 提交完会自己 reset 缓冲
            UPLOADER.draw(RAW);
        } finally {
            snapshot.restore();
            RAW.setTranslation(0, 0, 0);
        }
    }

    /**
     * 第一次开批时自检公告板向量：两者都该是单位向量、互相垂直。
     * <p>按 {@link ActiveRenderInfo} 的取值推导，这两个向量恒为单位正交；偏差超过容差说明相机朝向被别的东西改过
     * （或者推导的前提变了），打一行日志方便定位「面片朝向不对」。</p>
     */
    private static void checkBillboardVectors() {
        float rightLen = (float) Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        float upLen = (float) Math.sqrt(upX * upX + upY * upY + upZ * upZ);
        float dot = rightX * upX + rightY * upY + rightZ * upZ;
        if (Math.abs(rightLen - 1.0F) > BILLBOARD_EPSILON
                || Math.abs(upLen - 1.0F) > BILLBOARD_EPSILON
                || Math.abs(dot) > BILLBOARD_EPSILON) {
            LogUtil.warn("公告板向量自检异常：|right| = %.5f，|up| = %.5f，right·up = %.5f（right = (%.4f, %.4f, %.4f)，up = (%.4f, %.4f, %.4f)）",
                    rightLen, upLen, dot, rightX, rightY, rightZ, upX, upY, upZ);
        } else {
            LogUtil.info("公告板向量自检通过：|right| = %.5f，|up| = %.5f，right·up = %.5f", rightLen, upLen, dot);
        }
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
     * <p>渲染器应在最开头调用并判空：返回 null 表示本帧批次未开启（如世界尚未加载），
     * 此时应直接返回、不做任何绘制。</p>
     *
     * @return 共享顶点缓冲；批次未开启时为 null
     */
    @Nullable
    public static BufferBuilder builder() {
        return building ? BUILDER : null;
    }

    /**
     * 1.12.2 版：恒为单位矩阵（模型视图矩阵里已经带着相机变换，见类注释）。
     * 保留这个方法是为了让各渲染器的写法和 1.20.1 一行对一行。
     *
     * @return 本帧变换矩阵
     */
    public static Matrix4f matrix() {
        return Matrix4f.IDENTITY;
    }

    /**
     * @return 本帧相机世界坐标；还没开过批时为 null
     */
    @Nullable
    public static Vec3d cameraPosition() {
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
