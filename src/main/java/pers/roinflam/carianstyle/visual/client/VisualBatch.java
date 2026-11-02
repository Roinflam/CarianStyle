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
 * PORT-STUB(B2b)
 *
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
 * @author RoinFlam
 * @version 1.1
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class VisualBatch {
    private static final int BUFFER_CAPACITY = 2097152;
    private static final BufferBuilder BUILDER = null; // PORT-STUB
    private VisualBatch() {
    }
    @Nullable public static BufferBuilder builder() {
        return null;
    }
    @Nullable public static Matrix4f matrix() {
        return null;
    }
    @Nullable public static Vec3 cameraPosition() {
        return null;
    }
    public static float partialTick() {
        return 0.0F;
    }
    public static int frameId() {
        return 0;
    }
    public static float rightX() {
        return 0.0F;
    }
    public static float rightY() {
        return 0.0F;
    }
    public static float rightZ() {
        return 0.0F;
    }
    public static float upX() {
        return 0.0F;
    }
    public static float upY() {
        return 0.0F;
    }
    public static float upZ() {
        return 0.0F;
    }
}

