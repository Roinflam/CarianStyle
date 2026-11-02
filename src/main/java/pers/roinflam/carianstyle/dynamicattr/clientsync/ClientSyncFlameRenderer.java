package pers.roinflam.carianstyle.dynamicattr.clientsync;

import com.mojang.blaze3d.vertex.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.client.VisualLod;
import pers.roinflam.carianstyle.config.ClientVisualConfig;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V3)
 *
 * 客户端火焰渲染处理器
 * <p>
 * 负责渲染三种自定义火焰效果：
 * - 序列号1：注定死亡火焰（猩红色）
 * - 序列号2：毁灭火焰（白色）
 * - 序列号3：癫痫火焰（黄色）
 * </p>
 * <p>
 * 视觉说明（材质不变，仅控制渲染表现）：
 * 1. 渲染类型使用 cutout（与原版火焰、树叶一致）：alpha 测试为二值——纹理不透明像素
 *    完全绘制、透明像素直接丢弃，火焰完全不透明、不会透出背景。
 * 2. 火焰为稳定竖直形态，不做任何左右摆动 / 横向飘动 / 大小缩放。
 * 3. 火焰亮度由 uv2(240,240) 拉满光照实现，黑暗中依旧明亮。
 * 4. 火焰外形（含火尖）完全由纹理本身的透明轮廓决定，不再用 alpha 做淡出。
 * 5. 唯一保留的动画是非常细微的亮度闪烁（仅调制顶点 RGB，不改色相）；
 *    若需完全静止，将 FLICKER_AMPLITUDE 设为 0 即可。
 * 6. 性能：ResourceLocation 预创建，避免每帧每实体重复分配对象（渲染热路径）。
 * </p>
 * <p>
 * 注意：cutout 使用 BLOCK 顶点格式（position/color/uv/uv2/normal），不含 overlay 元素，
 * 因此顶点写入不调用 overlayCoords。
 * </p>
 *
 * <h3>v4：白焰改为第三人称专属（第一人称不再绘制）</h3>
 * <p>
 * <b>起因：</b>黑焰仪式在条件满足时会用 {@code onPlayerTick} <b>每 20 tick 刷新一次</b>
 * 21 tick 时长的白焰，也就是条件持续成立期间火焰是<b>无缝常亮</b>的。
 * 而第一人称的手部火焰永远贴在镜头前——一团持续不断的火挡在视野正中，
 * 时间一长是纯粹的干扰，与「短暂着火提示你中了效果」完全是两回事。
 * </p>
 * <p>
 * 改法是给 {@link FlameConfig} 加一个 {@code renderInFirstPerson} 开关，白焰设为
 * {@code false}。第三人称、别人看你、以及 {@code onRenderLevelStage} 的补渲染<b>全都不受影响</b>，
 * 只有 {@link #onRenderHand} 这一条路径会跳过它。
 * </p>
 *
 * <h4>已知的连带影响：黑焰刀刃</h4>
 * <p>
 * 序列号 2（白焰）是<b>黑焰仪式和黑焰刀刃共用</b>的，而黑焰刀刃是把火挂在<b>被打的人</b>身上。
 * 因此这样做的连带结果是：<b>你被别人的黑焰刀刃打中时，第一人称也看不到自己在烧</b>，
 * 只能靠第三人称或别人的视角看到。
 * </p>
 * <p>
 * 这是<b>明知并接受</b>的取舍。要精确到「仪式的火不画、刀刃的火照画」，就得给两者拆出
 * 两个序列号，代价是 {@code ClientSyncEffectManager} 的已知序列号表再长一格、
 * 且要多维护一套完全同色同贴图的配置。为了一个「自己视野里短暂看不到火」的差别付这个代价，
 * 不划算——何况原版着火的火焰是另一套渲染，不受此处影响，真着火时第一人称照样看得见。
 * </p>
 *
 * <h4>为什么用 break 而不是 continue</h4>
 * <p>
 * 跳过时直接 {@code break}，<b>不往下找别的火焰</b>。第三人称那边的选择逻辑同样是
 * 「命中第一个就 break」，两处必须用同一套优先级；若第一人称改用 {@code continue} 找替补，
 * 就会出现<b>第三人称烧白焰、第一人称烧黄焰</b>这种两个视角对不上的情况。
 * </p>
 *
 * <h3>v2 性能：接入 {@link VisualLod}（含一处必须处理的时序坑）</h3>
 * <p>
 * 本渲染器此前是<b>唯一完全没接入统一视觉体系</b>的世界渲染器——它既不做距离裁剪，
 * 也不登记同屏实例数。前者影响不大（顶点量本来就低，见下），
 * <b>后者才是真问题</b>。
 * </p>
 *
 * <h4>为什么「不登记实例数」是真问题</h4>
 * <p>
 * {@link VisualLod} 的拥挤度系数 {@code crowdFactor} 是<b>全局共享</b>的：
 * 它按「上一帧全部渲染器登记的实例总数」估算同屏拥挤程度，再据此统一削减。
 * 只要有渲染器不登记，这个总数就被<b>系统性低估</b>，
 * 于是已接入 LOD 的重量级渲染器（黄金树祝福约 2000 顶点、重力力场圈约 2112 顶点、
 * 猩红腐败女神约 1872 顶点）在团战时<b>削减不足</b>。
 * </p>
 * <p>
 * 换句话说：补上这一行的收益<b>不在火焰自己身上，而在别的渲染器身上</b>。
 * 火焰自己的顶点量粗算只有：
 * </p>
 * <pre>
 * 单个着火实体 ≈ 5 层 × 4 顶点 = 20 顶点
 * </pre>
 * <p>
 * ——比出血的 948 低了整整两个数量级，削它本身几乎没有意义。
 * 下方的层数削减（{@link #FLAME_STEP_DETAIL_FLOOR}）只是顺手做的一致性处理，
 * 远处能省下两三层，聊胜于无，<b>不要指望它带来可观测的帧率变化</b>。
 * </p>
 *
 * <h4>时序坑：不能直接在这里调 countInstance</h4>
 * <p>
 * 本渲染器挂的是 {@link RenderLivingEvent.Post}，而在 {@code LevelRenderer.renderLevel}
 * 的流程里，<b>实体渲染发生在 {@code AFTER_TRANSLUCENT_BLOCKS} 之前</b>：
 * </p>
 * <pre>
 * ... → solid/cutout 区块层 → <b>实体渲染（本渲染器在此）</b> → AFTER_ENTITIES
 *     → 方块实体 → translucent 区块层 → <b>AFTER_TRANSLUCENT_BLOCKS（VisualBatch 在此）</b>
 * </pre>
 * <p>
 * 而 {@code VisualBatch.onBatchBegin}（HIGHEST 优先级）里会调用
 * {@code VisualLod.beginFrame()}，后者做的第一件事就是
 * {@code currentFrameInstances = 0}。
 * </p>
 * <p>
 * <b>因此若在 {@link #onRenderLiving} 里直接 {@code countInstance()}，
 * 这次计数会在紧接着的 beginFrame 里被当场清零，一次都统计不到</b>——
 * 等于什么都没做，还平白多了一行看起来正确的死代码。
 * </p>
 * <p>
 * <b>解法：</b>火焰渲染时只把数量攒在 {@link #pendingInstances} 里，
 * 由 {@link #onRenderLevelStage} 在 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段
 * （默认 NORMAL 优先级，正好夹在 VisualBatch 的 HIGHEST 与 LOWEST 之间）
 * 于 {@code beginFrame} 已经复位之后再一次性补报。
 * 这样计数落在<b>本帧</b>的正确位置上。
 * </p>
 *
 * <h4>已知且可接受的一处偏差</h4>
 * <p>
 * 火焰读取 {@code VisualLod.detail()} 时，{@code crowdFactor} 还是
 * <b>本帧 beginFrame 之前</b>的值——也就是基于「上上帧」实例数算出来的。
 * 其余渲染器读到的是基于「上一帧」的。
 * </p>
 * <p>
 * 也就是说火焰的拥挤度判断比别人晚一帧。这完全可以接受：
 * {@link VisualLod} 本身就是用上一帧估算的（同屏特效数量在相邻帧高度连续，
 * 这是实时渲染里的标准做法），再晚一帧不改变量级。
 * 而 {@code distanceFactor} 是纯函数、不受影响，那才是火焰这种小顶点量元素的主要削减依据。
 * </p>
 *
 * <h3>v3 健壮性：只在「主世界实体渲染流程」里画火焰</h3>
 *
 * <h4>问题：无条件信任事件传进来的 MultiBufferSource</h4>
 * <p>
 * v2 之前，{@link #onRenderLiving} 拿到 {@code event.getMultiBufferSource()} 就直接
 * {@code getBuffer(RenderType.cutout())} 往里写顶点，<b>完全不判断这次调用是从哪来的</b>。
 * </p>
 * <p>
 * 正常世界渲染下这没问题。但 {@link RenderLivingEvent.Post} 是<b>只要有人调实体渲染就会触发</b>的，
 * 而调用方远不止原版的世界渲染循环：
 * </p>
 * <ul>
 *     <li><b>残影 / 分身类粒子</b>——不少模组的 Boss
 *         会在<b>粒子渲染阶段</b>把同一个实体<b>再渲染一遍</b>做残影，
 *         并塞进一个自己包装的 {@link MultiBufferSource}（通常是为了改 alpha）。
 *         那种包装往往<b>忽略传入的 RenderType</b>、返回它自己那条管线的
 *         {@code VertexConsumer}；</li>
 *     <li><b>GUI 里的实体预览</b>——物品栏的玩家模型、生物图鉴之类，
 *         走的是 {@code InventoryScreen.renderEntityInInventory} → 同一套实体渲染；</li>
 *     <li>其它在实体渲染过程中做二次渲染的模组。</li>
 * </ul>
 * <p>
 * <b>失败链路：</b>
 * </p>
 * <pre>
 * 我们请求 RenderType.cutout()   → 期望 BLOCK 顶点格式（position/color/uv/uv2/normal，<b>无 overlay</b>）
 * 包装实际返回的 consumer        → 可能是 ENTITY 格式（<b>含 overlay</b>）
 * endVertex()                    → IllegalStateException: Not filled all elements of the vertex
 * </pre>
 * <p>
 * 崩溃栈会指向<b>我们的</b> {@code endVertex()}，但根因是我们在一个不属于自己的
 * 渲染流程里写了顶点。
 * </p>
 * <p>
 * <b>这不针对任何特定模组或实体。</b>触发它的怪本身渲染完全正常，
 * 换成任何一个会用同类残影粒子的实体，结果一样——所以修复也必须是通用的，
 * 不能去认某个实体的 id。
 * </p>
 *
 * <h4>修复：两道互补的准入检查</h4>
 * <p>
 * 关键在于「先判断这次调用是不是主世界实体渲染」，而不是事后 try-catch 兜异常
 * （兜住之后顶点缓冲可能已处于半写状态，继续渲染只会更糟）。两道检查缺一不可：
 * </p>
 * <ol>
 *     <li><b>渲染阶段窗口</b>（{@link #inMainEntityPass}）——由 {@link #onRenderLevelStage}
 *         在 {@code AFTER_CUTOUT_BLOCKS} 开窗、在其余任何阶段关窗。
 *         实体渲染循环正好夹在 {@code AFTER_CUTOUT_BLOCKS} 与 {@code AFTER_ENTITIES} 之间，
 *         因此窗口精确覆盖主实体渲染，把<b>粒子阶段的残影</b>与
 *         <b>GUI 里的实体预览</b>（都在窗口之外）全部挡在门外；</li>
 *     <li><b>只写主缓冲</b>（{@link #isMainBufferSource}）——事件传来的不是
 *         {@code Minecraft.renderBuffers().bufferSource()} 那一个实例时，改写主缓冲，
 *         不往别人包装的缓冲里写顶点。（v3 是直接跳过；但 Oculus 对每次实体渲染都会包一层，
 *         跳过等于装了 Oculus 就永远不画，v4 改成换缓冲。）</li>
 * </ol>
 * <p>
 * <b>为什么两道都要：</b>只写主缓冲挡不住 GUI 预览——{@code GuiGraphics} 持有的
 * 恰恰就是同一个 {@code bufferSource}；窗口单独用挡不住窗口内换了缓冲的重入。二者互补。
 * 窗口内的重入（例如残影）现在会在主缓冲里多画一团火，位置和本体相同或相近，不影响观感。
 * </p>
 *
 * <h4>失败方向是安全的</h4>
 * <p>
 * 窗口的开关依赖「{@code AFTER_CUTOUT_BLOCKS} 早于实体渲染循环」这一 Forge 阶段顺序。
 * 万一将来某个版本或某个 mixin 打乱了这个顺序，结果是<b>火焰不显示</b>，
 * 而不是崩溃或花屏——这正是防御代码该有的失败方向。
 * </p>
 * <p>
 * Oculus 会把实体渲染用的缓冲包一层（不开光影也包），所以不能按身份比对决定画不画，
 * 只能决定往哪里画（见上）。
 * <b>排查「火焰突然不见了」时，这两处检查是第一个该看的地方。</b>
 * </p>
 *
 * <h4>兜底熔断</h4>
 * <p>
 * 上面两道检查已经覆盖了已知的失败链路，但整合包环境里总有想不到的组合。
 * 因此 {@link #renderFlameGuarded} 再包一层：渲染抛异常时计数，
 * 累计到 {@link #MAX_RENDER_FAILURES} 次就<b>永久停用</b>火焰渲染并记一条日志。
 * </p>
 * <p>
 * <b>刻意不是「一次异常就停用」</b>——偶发的瞬时异常（例如材质图集正在重载）
 * 不该让功能永久消失；也<b>刻意不每次都打完整堆栈</b>——只有第一次打，
 * 之后静默计数，否则渲染热路径上的日志刷屏本身就会把游戏卡死，
 * 那比原来的崩溃还难排查。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public class ClientSyncFlameRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final float FLICKER_AMPLITUDE = 0.08F;
    private static final float FLICKER_SPEED = 0.60F;
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float FLAME_LAYER_STEP = 0.45F;
    private static final float FLAME_LAYER_SHRINK = 0.9F;
    private static final float FLAME_LAYER_Z_ADVANCE = 0.03F;
    private static final float FLAME_STEP_DETAIL_FLOOR = 0.55F;
    private static final int MAX_REPORT_PER_FRAME = 256;
    private static final int MAX_RENDER_FAILURES = 8;

    private static class FlameConfig {
        int serialNumber;
        ResourceLocation layer0;
        ResourceLocation layer1;
        boolean renderInFirstPerson;
        FlameConfig(int serialNumber, String layer0, String layer1) {
            this(serialNumber, layer0, layer1, true);
        }
        FlameConfig(int serialNumber, String layer0, String layer1, boolean renderInFirstPerson) {
        }
    }
    private static final FlameConfig[] FLAME_CONFIGS = {
            // 注定死亡火焰（猩红色）- 序列号1
            new FlameConfig(1,
                    Reference.MOD_ID + ":block/crimson_flame_layer_0",
                    Reference.MOD_ID + ":block/crimson_flame_layer_1"),

            // 毁灭火焰（白色）- 序列号2
            // v4：第三人称专属。黑焰仪式在满足条件时会持续刷新这团火，
            //    贴在镜头前会长时间挡住视野（详见类注释「v4」一节）。
            new FlameConfig(2,
                    Reference.MOD_ID + ":block/white_flame_layer_0",
                    Reference.MOD_ID + ":block/white_flame_layer_1",
                    false),

            // 癫痫火焰（黄色）- 序列号3
            new FlameConfig(3,
                    Reference.MOD_ID + ":block/yellow_flame_layer_0",
                    Reference.MOD_ID + ":block/yellow_flame_layer_1")
    };
}

