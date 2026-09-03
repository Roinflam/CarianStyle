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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.client.VisualLod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
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
 * 因此本次改动的连带结果是：<b>你被别人的黑焰刀刃打中时，第一人称也看不到自己在烧</b>，
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
 * <h4>⚠ 时序坑：不能直接在这里调 countInstance</h4>
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
 *     <li><b>残影 / 分身类粒子</b>——不少模组的 Boss（如某些整合包里会放 AfterImage 残影的怪）
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
 *     <li><b>缓冲身份比对</b>（{@link #isMainBufferSource}）——主渲染流程传下来的
 *         必然是 {@code Minecraft.renderBuffers().bufferSource()} <b>那一个实例</b>。
 *         这道检查负责挡住「在实体渲染窗口<b>之内</b>做二次渲染、但换了缓冲」的情况。</li>
 * </ol>
 * <p>
 * <b>为什么两道都要：</b>身份比对单独用挡不住 GUI 预览——{@code GuiGraphics} 持有的
 * 恰恰就是同一个 {@code bufferSource}；窗口单独用挡不住窗口内的重入。二者互补。
 * </p>
 *
 * <h4>失败方向是安全的</h4>
 * <p>
 * 窗口的开关依赖「{@code AFTER_CUTOUT_BLOCKS} 早于实体渲染循环」这一 Forge 阶段顺序。
 * 万一将来某个版本或某个 mixin 打乱了这个顺序，结果是<b>火焰不显示</b>，
 * 而不是崩溃或花屏——这正是防御代码该有的失败方向。
 * </p>
 * <p>
 * 同理，若某个模组<b>合法地</b>替换了主 {@code bufferSource} 实例（目前已知的光影模组
 * 不会这么做，它们在 RenderType / shader 层做手脚），火焰同样只是不显示。
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
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public class ClientSyncFlameRenderer {

    /** 日志器：仅用于兜底熔断，正常运行不产生任何输出 */
    private static final Logger LOGGER = LogUtils.getLogger();

    // ==================== 火焰渲染参数（集中管理，便于手动调整） ====================

    /** 亮度闪烁幅度（0~1，越大明暗变化越明显；设为 0 则火焰亮度完全静止） */
    private static final float FLICKER_AMPLITUDE = 0.08F;
    /** 亮度闪烁速度 */
    private static final float FLICKER_SPEED = 0.60F;

    /**
     * 距离裁剪（格）：相机太远的着火实体本帧不绘制火焰。
     * <p>
     * 与其余世界渲染器取同值（48），保持一致。
     * </p>
     * <p>
     * <b>说明：</b>原版本身对实体渲染已有视锥 + 渲染距离裁剪，远处实体本就不会触发
     * {@link RenderLivingEvent}，因此本裁剪的实际收益很小；
     * 它存在的主要意义是给 {@link VisualLod#detail} 提供一个明确的距离上界，
     * 顺带兜住「其它模组把渲染距离调得极大」的情况。
     * </p>
     */
    private static final double CULL = 48.0;

    /** {@link #CULL} 的平方（避免开方） */
    private static final double CULL_SQR = CULL * CULL;

    /**
     * 火焰分层的基准步长（格，缩放空间内）。
     * <p>这是优化前写死的 {@code 0.45F}，提为常量以便与下方的 LOD 缩放联动。</p>
     */
    private static final float FLAME_LAYER_STEP = 0.45F;

    /** 每层火焰的横向收缩比例（优化前写死的 {@code 0.9F}） */
    private static final float FLAME_LAYER_SHRINK = 0.9F;

    /** 每层火焰的纵深推进量（优化前写死的 {@code 0.03F}） */
    private static final float FLAME_LAYER_Z_ADVANCE = 0.03F;

    /**
     * 层步长缩放的细节系数下限。
     * <p>
     * 实际步长为 {@code FLAME_LAYER_STEP / max(本值, detail)}：细节系数越低步长越大、层数越少，
     * 但最多只放大到 {@code 1 / 本值 ≈ 1.8} 倍（层数约减半）。
     * </p>
     * <p>
     * <b>刻意留得很保守。</b>火焰总共才 5 层左右，砍到 2 层会明显看出是几片分离的贴图
     * 而不是一团火；而省下的那两层不过 8 个顶点，完全不值得拿观感去换
     * （详见类注释「为什么『不登记实例数』是真问题」）。
     * </p>
     */
    private static final float FLAME_STEP_DETAIL_FLOOR = 0.55F;

    /**
     * 单帧补报实例数的上限（防御性）。
     * <p>
     * 正常情况下 {@link #onRenderLevelStage} 每帧都会补报并清零，
     * {@link #pendingInstances} 不会累积。设此上限只是兜住极端异常
     * （例如世界渲染阶段被其它模组异常中断），避免补报循环空转过久。
     * 256 远大于任何合理的同屏着火实体数。
     * </p>
     */
    private static final int MAX_REPORT_PER_FRAME = 256;

    /**
     * 兜底熔断阈值：火焰渲染累计抛出这么多次异常后<b>永久停用</b>。
     * <p>
     * 取 {@value} 而非 1，是因为偶发的瞬时异常（材质图集重载、某帧的资源竞态）
     * 不该让功能永久消失；而真正的系统性不兼容会在几帧内就把计数打满。
     * </p>
     */
    private static final int MAX_RENDER_FAILURES = 8;

    /**
     * 本帧已绘制的火焰实例数，等待补报给 {@link VisualLod}。
     * <p>
     * <b>不能在绘制时直接 {@code countInstance()}</b>——实体渲染早于
     * {@code VisualBatch.onBatchBegin}，那次计数会被 {@code beginFrame} 当场清零
     * （详见类注释「⚠ 时序坑」）。故先攒在这里，由 {@link #onRenderLevelStage} 补报。
     * </p>
     * <p>仅客户端渲染线程访问，无并发问题。</p>
     */
    private static int pendingInstances = 0;

    /**
     * 当前是否处于「主世界实体渲染」的阶段窗口内（v3 新增）。
     * <p>
     * 由 {@link #onRenderLevelStage} 在 {@code AFTER_CUTOUT_BLOCKS} 置 true、
     * 在其余任何渲染阶段置 false。实体渲染循环正好夹在
     * {@code AFTER_CUTOUT_BLOCKS} 与 {@code AFTER_ENTITIES} 之间，
     * 因此这个窗口精确覆盖主实体渲染。
     * </p>
     * <p>
     * <b>自动兜底：</b>「除开窗阶段外一律关窗」这个写法意味着，
     * 即便某帧的世界渲染被异常中断、窗口没能正常关闭，
     * 下一帧最早的 {@code AFTER_SKY} 也会立刻把它关上，不会长期残留。
     * </p>
     * <p>仅客户端渲染线程访问，无并发问题。</p>
     */
    private static boolean inMainEntityPass = false;

    /**
     * 火焰渲染累计失败次数（v3 新增，兜底熔断用）。
     * <p>达到 {@link #MAX_RENDER_FAILURES} 后 {@link #renderDisabled} 置位、永久停用。</p>
     */
    private static int renderFailures = 0;

    /**
     * 火焰渲染是否已被熔断永久停用（v3 新增）。
     * <p>置位后本渲染器的两个绘制入口都会直接返回，直到游戏重启。</p>
     */
    private static boolean renderDisabled = false;

    /**
     * 火焰效果配置
     */
    private static class FlameConfig {
        final int serialNumber;
        // 预创建 ResourceLocation，避免每帧渲染重复 new 对象
        final ResourceLocation layer0;
        final ResourceLocation layer1;

        /**
         * 是否在第一人称（手部渲染）中绘制本种火焰（v4 新增）。
         * <p>
         * {@code false} 表示该火焰<b>只有别人和第三人称视角能看到</b>，
         * 玩家自己在第一人称下视野干净。详见类注释「v4」一节。
         * </p>
         */
        final boolean renderInFirstPerson;

        /**
         * 三参构造：默认<b>第一人称也绘制</b>，与 v4 之前的行为完全一致。
         * <p>保留它是为了让不需要改变行为的火焰配置保持原样，避免无谓的 diff。</p>
         */
        FlameConfig(int serialNumber, String layer0, String layer1) {
            this(serialNumber, layer0, layer1, true);
        }

        /**
         * 四参构造：显式指定是否在第一人称绘制（v4 新增）。
         *
         * @param serialNumber        客户端同步序列号
         * @param layer0              火焰外层纹理
         * @param layer1              火焰内层纹理
         * @param renderInFirstPerson 是否在第一人称手部渲染中绘制
         */
        FlameConfig(int serialNumber, String layer0, String layer1, boolean renderInFirstPerson) {
            this.serialNumber = serialNumber;
            this.layer0 = new ResourceLocation(layer0);
            this.layer1 = new ResourceLocation(layer1);
            this.renderInFirstPerson = renderInFirstPerson;
        }
    }

    /**
     * 三种火焰配置
     * 根据你的实际纹理路径修改
     */
    private static final FlameConfig[] FLAME_CONFIGS = {
            // 注定死亡火焰（猩红色）- 序列号1
            new FlameConfig(1,
                    Reference.MOD_ID + ":block/crimson_flame_layer_0",
                    Reference.MOD_ID + ":block/crimson_flame_layer_1"),

            // 毁灭火焰（白色）- 序列号2
            // ⭐ v4：第三人称专属。黑焰仪式在满足条件时会持续刷新这团火，
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

    /**
     * 渲染阶段回调：维护主实体渲染窗口，并补报本帧的火焰实例数。
     * <p>
     * <b>两件事合并在一个监听器里</b>，因为它们订阅的是同一个事件、
     * 分开写会平白多一次事件分发。
     * </p>
     * <p>
     * <b>窗口的开关（v3）：</b>{@code AFTER_CUTOUT_BLOCKS} 开窗，
     * <b>其余任何阶段一律关窗</b>。实体渲染循环夹在 {@code AFTER_CUTOUT_BLOCKS}
     * 与 {@code AFTER_ENTITIES} 之间，因此窗口精确覆盖主实体渲染；
     * 而「除开窗阶段外一律关窗」的写法顺带提供了自动兜底——
     * 即便某帧世界渲染被中断、窗口没正常关，下一帧最早的
     * {@code AFTER_SKY} 也会立刻关上它。
     * </p>
     * <p>
     * <b>实例补报（v2）：</b>在 {@code AFTER_TRANSLUCENT_BLOCKS} 阶段、
     * 使用<b>默认的 NORMAL 优先级</b>——这样它正好夹在 {@code VisualBatch.onBatchBegin}
     * （HIGHEST，其中会 {@code beginFrame()} 复位计数器）与 {@code onBatchEnd}（LOWEST）之间，
     * 补报的数字能正确计入本帧。
     * </p>
     * <p>
     * <b>为什么是循环调用而不是加一个批量方法：</b>
     * {@link VisualLod} 目前只暴露单个 {@code countInstance()}，为这一处去改它的公开接口
     * 收益不成比例；而同屏着火实体数是个位到十几的量级，循环成本可忽略。
     * </p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RenderLevelStageEvent.Stage stage = event.getStage();

        if (stage == RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS) {
            // 实体渲染循环即将开始：开窗
            inMainEntityPass = true;
            return;
        }

        // 其余任何阶段都意味着「不在主实体渲染循环里」：关窗。
        // 这样写而不是只在 AFTER_ENTITIES 关，是为了自动兜底（详见方法注释）
        inMainEntityPass = false;

        if (stage != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        int n = pendingInstances;
        pendingInstances = 0;
        if (n <= 0) {
            return;
        }
        if (n > MAX_REPORT_PER_FRAME) {
            n = MAX_REPORT_PER_FRAME;
        }
        for (int i = 0; i < n; i++) {
            VisualLod.countInstance();
        }
    }

    /**
     * 实体渲染后事件：渲染实体身上的火焰效果
     * <p>
     * v2：新增距离裁剪与 {@link VisualLod} 细节系数；实例登记改为攒进
     * {@link #pendingInstances}、由 {@link #onRenderLevelStage} 补报
     * （原因详见类注释的「⚠ 时序坑」小节）。
     * </p>
     * <p>
     * v3：新增两道准入检查，确保只在<b>主世界实体渲染流程</b>里画火焰——
     * 挡住残影粒子的二次渲染与 GUI 里的实体预览
     * （原因与失败链路详见类注释的「v3 健壮性」小节）。
     * </p>
     *
     * @param event 实体渲染后事件
     */
    @SubscribeEvent
    public static void onRenderLiving(@Nonnull RenderLivingEvent.Post<?, ?> event) {
        if (renderDisabled) {
            return;
        }
        // ⭐ v3 检查一：必须处于主实体渲染的阶段窗口内。
        // 粒子阶段的残影重渲染、GUI 里的实体预览都落在窗口之外
        if (!inMainEntityPass) {
            return;
        }
        // ⭐ v3 检查二：必须是主渲染流程那一个 bufferSource 实例。
        // 挡住「在窗口之内做二次渲染、但换了缓冲」的情况——
        // 那种缓冲往往忽略我们请求的 RenderType，返回顶点格式不匹配的 consumer，
        // 写进去会在 endVertex() 抛 "Not filled all elements of the vertex"
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        if (!isMainBufferSource(bufferSource)) {
            return;
        }

        LivingEntity entity = event.getEntity();
        int entityId = entity.getId();
        // 帧间插值系数，用于保证亮度闪烁逐帧平滑
        float partialTick = event.getPartialTick();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, entityId)) {
                // ⭐ v2：距离裁剪 + 细节系数。相机不可用时按满细节处理（不裁剪），
                // 保证任何异常情况下火焰都不会凭空消失——宁可多画也不能漏画
                float detail = 1f;
                Minecraft mc = Minecraft.getInstance();
                if (mc.getEntityRenderDispatcher().camera != null) {
                    Vec3 cam = mc.getEntityRenderDispatcher().camera.getPosition();
                    double ex = Mth.lerp((double) partialTick, entity.xo, entity.getX());
                    double ey = Mth.lerp((double) partialTick, entity.yo, entity.getY());
                    double ez = Mth.lerp((double) partialTick, entity.zo, entity.getZ());
                    double dx = ex - cam.x;
                    double dy = ey - cam.y;
                    double dz = ez - cam.z;
                    double distSqr = dx * dx + dy * dy + dz * dz;
                    if (distSqr > CULL_SQR) {
                        // 太远：整层跳过（连实例也不登记——它对同屏拥挤度确实没有贡献）
                        break;
                    }
                    detail = VisualLod.detail(distSqr);
                }

                // ⭐ v2：攒着，不能在这里直接 countInstance（会被 beginFrame 清零）
                pendingInstances++;

                renderFlameGuarded(
                        entity,
                        event.getPoseStack(),
                        bufferSource,
                        config.layer0,
                        config.layer1,
                        partialTick,
                        detail
                );
                // 只渲染第一个匹配的火焰（避免多个火焰叠加）
                break;
            }
        }
    }

    /**
     * 判断给定的 {@link MultiBufferSource} 是否为<b>主世界渲染流程</b>使用的那一个实例。
     * <p>
     * 原版的 {@code LevelRenderer.renderLevel} 与 {@code GameRenderer.renderItemInHand}
     * 传下来的都是 {@code Minecraft.renderBuffers().bufferSource()}——
     * 它是 {@code RenderBuffers} 持有的字段，整个游戏生命周期内是同一个对象，
     * 因此可以直接做<b>引用比较</b>，零开销。
     * </p>
     * <p>
     * <b>这道检查挡的是什么：</b>残影 / 分身类粒子做二次实体渲染时，
     * 塞进来的通常是一个自己包装的 {@link MultiBufferSource}（为了改 alpha），
     * 那种包装往往忽略传入的 {@code RenderType}、返回它自己那条管线的
     * {@code VertexConsumer}，顶点格式与我们期望的 BLOCK 格式不一致。
     * </p>
     * <p>
     * <b>这道检查挡不住什么：</b>GUI 里的实体预览——{@code GuiGraphics} 持有的
     * 恰恰就是同一个 {@code bufferSource}，所以那种情况要靠
     * {@link #inMainEntityPass} 窗口来挡。二者互补，缺一不可。
     * </p>
     * <p>
     * <b>失败方向是安全的：</b>若某个模组合法地替换了主 {@code bufferSource} 实例，
     * 结果只是火焰不显示，不会崩溃或花屏。
     * </p>
     *
     * @param source 事件传入的缓冲源，可能为 null（防御性）
     * @return 是主渲染流程的缓冲源返回 true
     */
    private static boolean isMainBufferSource(@Nullable MultiBufferSource source) {
        if (source == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        // renderBuffers() 在极早期（资源加载中）理论上可能尚未就绪，做一次判空
        if (mc.renderBuffers() == null) {
            return false;
        }
        return source == mc.renderBuffers().bufferSource();
    }

    /**
     * 带兜底熔断的火焰渲染包装（v3 新增）。
     * <p>
     * 前面两道准入检查已经覆盖了已知的失败链路，但整合包环境里总有想不到的组合，
     * 因此再包一层：渲染抛异常时计数，累计到 {@link #MAX_RENDER_FAILURES} 次就
     * <b>永久停用</b>火焰渲染。
     * </p>
     * <p>
     * <b>为什么不是「一次异常就停用」：</b>偶发的瞬时异常（材质图集正在重载、
     * 某帧的资源竞态）不该让功能永久消失；而真正的系统性不兼容会在几帧内就把计数打满。
     * </p>
     * <p>
     * <b>为什么只有第一次打完整堆栈：</b>这是每帧每实体都会走的渲染热路径，
     * 在这里日志刷屏本身就会把游戏卡死——那比原来的崩溃还难排查。
     * 所以首次记完整堆栈用于定位，之后静默计数，熔断时再记一条结论。
     * </p>
     *
     * @param entity        目标实体
     * @param poseStack     渲染矩阵栈
     * @param bufferSource  缓冲区源（已通过 {@link #isMainBufferSource} 校验）
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     * @param partialTick   帧间插值系数
     * @param detail        本帧细节系数（1.0 为满细节）
     */
    private static void renderFlameGuarded(@Nonnull Entity entity,
                                           @Nonnull PoseStack poseStack,
                                           @Nonnull MultiBufferSource bufferSource,
                                           @Nonnull ResourceLocation layer0Texture,
                                           @Nonnull ResourceLocation layer1Texture,
                                           float partialTick,
                                           float detail) {
        try {
            renderEntityOnFire(entity, poseStack, bufferSource,
                    layer0Texture, layer1Texture, partialTick, detail);
        } catch (Exception | LinkageError e) {
            renderFailures++;
            if (renderFailures == 1) {
                // 首次：记完整堆栈用于定位（之后静默，避免渲染热路径日志刷屏）
                LOGGER.error("[CarianStyle] 自定义火焰渲染失败，已记录本次堆栈；"
                        + "后续失败将静默计数，累计 {} 次后停用该效果。", MAX_RENDER_FAILURES, e);
            }
            if (renderFailures >= MAX_RENDER_FAILURES) {
                renderDisabled = true;
                LOGGER.error("[CarianStyle] 自定义火焰渲染累计失败 {} 次，已停用（重启游戏后恢复）。"
                        + "通常意味着有其它模组在实体渲染流程中做了本模组未预期的重入渲染。",
                        MAX_RENDER_FAILURES);
            }
        }
    }

    /**
     * 手部渲染事件：渲染第一人称火焰
     * <p>
     * <b>刻意不做 LOD、也不登记实例</b>：第一人称手部火焰永远贴在镜头前，
     * 距离恒为 0（满细节），削减它没有任何意义；而它与第三人称的自身火焰是同一个实体，
     * 登记两次会让拥挤度重复计数。
     * </p>
     * <p>
     * <b>v3：同样做缓冲身份校验，但不做阶段窗口检查。</b>
     * 第一人称手部渲染发生在 {@code GameRenderer.renderItemInHand}，
     * 那已经在 {@code LevelRenderer.renderLevel} 全部阶段<b>之后</b>——
     * 窗口此时必然是关的，加上窗口检查会让第一人称火焰永远不显示。
     * 身份校验则依然成立（它拿到的仍是主 {@code bufferSource}）。
     * </p>
     *
     * @param event 手部渲染事件
     */
    @SubscribeEvent
    public static void onRenderHand(@Nonnull RenderHandEvent event) {
        if (renderDisabled) {
            return;
        }
        MultiBufferSource bufferSource = event.getMultiBufferSource();
        if (!isMainBufferSource(bufferSource)) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        int playerId = minecraft.player.getId();
        // 连续动画时间：玩家存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = minecraft.player.tickCount + event.getPartialTick();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, playerId)) {
                // ⭐ v4：第三人称专属的火焰在这里止步。
                // 用 break 而不是 continue —— 第三人称那边同样是「命中第一个就 break」，
                // 若这里改用 continue 往下找，会出现「第三人称烧白焰、第一人称烧黄焰」
                // 这种两个视角不一致的情况。跳过就是彻底不画，不找替补。
                if (!config.renderInFirstPerson) {
                    break;
                }
                try {
                    renderFireInFirstPerson(
                            config.layer1,
                            event.getPoseStack(),
                            bufferSource,
                            time
                    );
                } catch (Exception | LinkageError e) {
                    // 与第三人称共用同一套熔断计数（同源问题不该被拆成两份统计）
                    renderFailures++;
                    if (renderFailures == 1) {
                        LOGGER.error("[CarianStyle] 第一人称火焰渲染失败，已记录本次堆栈；"
                                + "后续失败将静默计数，累计 {} 次后停用该效果。", MAX_RENDER_FAILURES, e);
                    }
                    if (renderFailures >= MAX_RENDER_FAILURES) {
                        renderDisabled = true;
                        LOGGER.error("[CarianStyle] 自定义火焰渲染累计失败 {} 次，已停用（重启游戏后恢复）。",
                                MAX_RENDER_FAILURES);
                    }
                }
                // 只渲染第一个匹配的火焰
                break;
            }
        }
    }

    /**
     * 渲染实体身上的火焰效果
     * <p>
     * v2：新增 {@code detail} 参数控制分层步长——细节系数越低、步长越大、层数越少。
     * 火焰的<b>总高度不变</b>（{@code startHeight} 与循环终止条件都没动），
     * 变的只是这段高度被切成几片。横向收缩比与纵深推进量按步长同比换算
     * （{@code pow(0.9, step/0.45)}、{@code 0.03 × step/0.45}），
     * 使削减后火焰的整体轮廓与全细节时保持近似。
     * </p>
     * <p>
     * {@code detail = 1} 时 {@code step} 恰为 {@link #FLAME_LAYER_STEP}(0.45)、
     * {@code shrink} 恰为 {@link #FLAME_LAYER_SHRINK}(0.9)、
     * {@code zAdvance} 恰为 {@link #FLAME_LAYER_Z_ADVANCE}(0.03)，
     * <b>与优化前逐像素一致</b>。
     * </p>
     *
     * @param entity        目标实体
     * @param poseStack     渲染矩阵栈
     * @param bufferSource  缓冲区源
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     * @param partialTick   帧间插值系数
     * @param detail        本帧细节系数（1.0 为满细节）
     */
    private static void renderEntityOnFire(@Nonnull Entity entity,
                                           @Nonnull PoseStack poseStack,
                                           @Nonnull MultiBufferSource bufferSource,
                                           @Nonnull ResourceLocation layer0Texture,
                                           @Nonnull ResourceLocation layer1Texture,
                                           float partialTick,
                                           float detail) {
        Minecraft minecraft = Minecraft.getInstance();

        TextureAtlasSprite fireLayer0 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(layer0Texture);
        TextureAtlasSprite fireLayer1 = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(layer1Texture);

        // 连续动画时间：实体存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = entity.tickCount + partialTick;
        // 每个实体使用独立相位，使多个火焰的亮度闪烁不完全同步
        float phase = entity.getId() * 0.6F;

        poseStack.pushPose();

        // 固定缩放，不做呼吸脉动
        float scale = entity.getBbWidth() * 1.4F;
        poseStack.scale(scale, scale, scale);

        // 火焰起始高度（缩放空间内）
        float startHeight = entity.getBbHeight() / scale;

        // billboard：火焰始终正面朝向摄像机（不叠加任何摆动）
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(
                -minecraft.getEntityRenderDispatcher().camera.getYRot()));

        poseStack.translate(0.0F, 0.0F, -0.3F + (float) ((int) startHeight) * 0.02F);

        float renderX = 0.5F;
        float renderY = 0.0F;
        float renderZ = 0.0F;
        float height = startHeight;
        int stage = 0;

        // ⭐ v2：按细节系数放大分层步长（层数变少、总高度不变），
        // 横向收缩与纵深推进同比换算，保证削减后轮廓与全细节时近似。
        // 三者在循环外算一次即可——放在循环里会让每层多一次 Math.pow
        float step = FLAME_LAYER_STEP / Math.max(FLAME_STEP_DETAIL_FLOOR, detail);
        float stepRatio = step / FLAME_LAYER_STEP;
        float shrink = (float) Math.pow(FLAME_LAYER_SHRINK, stepRatio);
        float zAdvance = FLAME_LAYER_Z_ADVANCE * stepRatio;

        // cutout 渲染类型：alpha 测试二值，火焰完全不透明，不会透出背景
        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        Matrix4f matrix4f = poseStack.last().pose();
        Matrix3f matrix3f = poseStack.last().normal();

        while (height > 0.0F) {
            TextureAtlasSprite sprite = (stage % 2 == 0) ? fireLayer0 : fireLayer1;

            float minU = sprite.getU0();
            float minV = sprite.getV0();
            float maxU = sprite.getU1();
            float maxV = sprite.getV1();

            if (stage / 2 % 2 == 0) {
                float temp = maxU;
                maxU = minU;
                minU = temp;
            }

            // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
            float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                    * Mth.sin(time * FLICKER_SPEED + stage * 0.7F + phase);
            int rgb = clampColor((int) (255.0F * flicker));

            float left = -renderX;
            float right = renderX;
            float bottomY = -renderY;
            float topY = 1.4F - renderY;

            // 底部右顶点（alpha 固定 255，完全不透明）
            builder.vertex(matrix4f, right, bottomY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 底部左顶点
            builder.vertex(matrix4f, left, bottomY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部左顶点
            builder.vertex(matrix4f, left, topY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部右顶点
            builder.vertex(matrix4f, right, topY, renderZ)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            height -= step;
            renderY -= step;
            renderX *= shrink;
            renderZ += zAdvance;
            stage++;
        }

        poseStack.popPose();
    }

    /**
     * 渲染第一人称火焰效果
     *
     * @param textureLocation 火焰纹理位置
     * @param poseStack       渲染矩阵栈
     * @param bufferSource    缓冲区源
     * @param time            连续动画时间（玩家tick + 帧间插值）
     */
    private static void renderFireInFirstPerson(@Nonnull ResourceLocation textureLocation,
                                                @Nonnull PoseStack poseStack,
                                                @Nonnull MultiBufferSource bufferSource,
                                                float time) {
        Minecraft minecraft = Minecraft.getInstance();

        TextureAtlasSprite sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(textureLocation);

        float minU = sprite.getU0();
        float maxU = sprite.getU1();
        float minV = sprite.getV0();
        float maxV = sprite.getV1();

        // 与实体火焰统一使用 cutout，完全不透明
        VertexConsumer builder = bufferSource.getBuffer(RenderType.cutout());

        for (int i = 0; i < 2; i++) {
            // 左右两片火焰使用错开的相位，使亮度闪烁不完全同步
            float sidePhase = i * 2.5F;

            poseStack.pushPose();
            poseStack.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees((float) (i * 2 - 1) * 10.0F));

            Matrix4f matrix4f = poseStack.last().pose();
            Matrix3f matrix3f = poseStack.last().normal();

            // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
            float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                    * Mth.sin(time * FLICKER_SPEED + sidePhase);
            int rgb = clampColor((int) (255.0F * flicker));

            // 底部左顶点（alpha 固定 255，完全不透明）
            builder.vertex(matrix4f, -0.5F, -0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 底部右顶点
            builder.vertex(matrix4f, 0.5F, -0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, maxV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部右顶点
            builder.vertex(matrix4f, 0.5F, 0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(minU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            // 顶部左顶点
            builder.vertex(matrix4f, -0.5F, 0.5F, -0.5F)
                    .color(rgb, rgb, rgb, 255)
                    .uv(maxU, minV)
                    .uv2(240, 240)
                    .normal(matrix3f, 0.0F, 1.0F, 0.0F)
                    .endVertex();

            poseStack.popPose();
        }
    }

    /**
     * 将颜色分量限制在 0~255 范围内
     *
     * @param value 原始分量值
     * @return 限制后的分量值
     */
    private static int clampColor(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, 255);
    }
}
