package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import pers.roinflam.carianstyle.config.ClientVisualConfig;
import pers.roinflam.carianstyle.mc112.render.GlPresets;
import pers.roinflam.carianstyle.mc112.render.GlStateSnapshot;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import pers.roinflam.carianstyle.visual.client.VisualBatch;
import pers.roinflam.carianstyle.visual.client.VisualLod;

import javax.annotation.Nonnull;

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
 * 1. 渲染方式与原版火焰、树叶一致的 cutout：alpha 测试为二值（{@code GREATER 0.1}）——纹理不透明像素
 *    完全绘制、透明像素直接丢弃，火焰完全不透明、不会透出背景。
 * 2. 火焰为稳定竖直形态，不做任何左右摆动 / 横向飘动 / 大小缩放。
 * 3. 火焰全亮：画的时候关掉光照和光照贴图（对应 1.20.1 的 {@code uv2(240,240)}），黑暗中依旧明亮。
 * 4. 火焰外形（含火尖）完全由纹理本身的透明轮廓决定，不再用 alpha 做淡出。
 * 5. 唯一保留的动画是非常细微的亮度闪烁（仅调制顶点 RGB，不改色相）；
 *    若需完全静止，将 FLICKER_AMPLITUDE 设为 0 即可。
 * 6. 性能：贴图名预先拼好，避免每帧每实体重复拼字符串（渲染热路径）。
 * </p>
 *
 * <h3>1.12.2 版的画法</h3>
 * <p>
 * 1.20.1 往实体渲染的 {@code MultiBufferSource} 里写 cutout 顶点；1.12.2 没有这一层，
 * 改成自己画：绑定方块图集，用 {@code Tessellator} 开一批 {@code POSITION_TEX_COLOR} 的四边形，
 * GL 状态用 {@link GlPresets#FLAME}（第一人称 {@link GlPresets#FLAME_FIRST_PERSON}）包住，画完按进入前的真实状态还原。
 * </p>
 * <ul>
 *     <li><b>世界里的火</b>：{@link RenderLivingEvent.Post}。1.12.2 的 {@code RenderLivingBase.doRender}
 *         先 {@code popMatrix} 再发 Post，事件触发时已经<b>不在实体变换里</b>，所以先平移到事件给的
 *         相对坐标（实体脚下），再绕 Y 轴转 {@code -playerViewY} 正对相机，然后画；</li>
 *     <li><b>第一人称手上的火</b>：{@link RenderSpecificHandEvent}（1.20.1 是 {@code RenderHandEvent}）。
 *         它每只手各触发一次，这里按帧只画一次；</li>
 *     <li>1.20.1 挂在 {@code RenderLevelStageEvent} 上的两件事（主实体渲染窗口、补报实例数）
 *         分别改到 {@link EntityViewRenderEvent.CameraSetup} 和 {@link RenderWorldLastEvent} 上（见下）。</li>
 * </ul>
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
 * {@code false}。第三人称、别人看你都<b>不受影响</b>，只有 {@link #onRenderHand} 这一条路径会跳过它。
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
 * {@link VisualLod} 的拥挤度系数 {@code crowdFactor} 是<b>全局共享</b>的：
 * 它按「上一帧全部渲染器登记的实例总数」估算同屏拥挤程度，再据此统一削减。
 * 只要有渲染器不登记，这个总数就被<b>系统性低估</b>，
 * 于是已接入 LOD 的重量级渲染器（黄金树祝福、重力力场圈、猩红腐败女神）在团战时<b>削减不足</b>。
 * 补上登记的收益<b>不在火焰自己身上，而在别的渲染器身上</b>：单个着火实体约 5 层 × 4 顶点 = 20 顶点，
 * 削它本身几乎没有意义。下方的层数削减（{@link #FLAME_STEP_DETAIL_FLOOR}）只是顺手做的一致性处理。
 * </p>
 *
 * <h4>时序坑：不能直接在这里调 countInstance</h4>
 * <p>
 * 1.12.2 版：{@code EntityRenderer.renderWorldPass} 里实体渲染（本渲染器在此）早于
 * {@link RenderWorldLastEvent}（{@code VisualBatch} 在此），而 {@code VisualBatch.onBatchBegin}
 * （HIGHEST）一上来就 {@code VisualLod.beginFrame()} 把计数清零。
 * 若在 {@link #onRenderLiving} 里直接 {@code countInstance()}，这次计数会被当场清掉。
 * </p>
 * <p>
 * <b>解法：</b>火焰渲染时只把数量攒在 {@link #pendingInstances} 里，
 * 由 {@link #onRenderWorldLast}（默认 NORMAL 优先级，正好夹在 VisualBatch 的 HIGHEST 与 LOWEST 之间）
 * 在 {@code beginFrame} 复位之后一次性补报，计数落在<b>本帧</b>。
 * 火焰读到的拥挤度因此比别人晚一帧，同屏特效数量在相邻帧高度连续，可以接受；
 * 距离系数是纯函数、不受影响，那才是火焰的主要削减依据。
 * </p>
 *
 * <h3>v3 健壮性：只在「主世界实体渲染流程」里画火焰</h3>
 * <p>
 * {@link RenderLivingEvent.Post} 是<b>只要有人调实体渲染就会触发</b>的：背包界面里的玩家模型
 * （{@code GuiInventory.drawEntityOnScreen}）、生物图鉴之类的预览、一些模组做残影时的二次渲染都会触发它。
 * 在这些地方画火焰，轻则界面里冒出一团悬空的火，重则和别人的渲染状态打架。
 * </p>
 * <p>
 * <b>窗口</b>（{@link #inMainEntityPass}）：{@link EntityViewRenderEvent.CameraSetup} 开窗——它在
 * {@code renderWorldPass} 一开头的相机变换里触发，早于实体渲染；{@link #onRenderWorldLast} 关窗——
 * 世界渲染到此为止。界面预览都在世界渲染之后画，落在窗口之外。
 * 1.20.1 还有第二道「只写主缓冲」检查，那是为了别人包装的 {@code MultiBufferSource} 顶点格式不对；
 * 1.12.2 版自己开一批画，没有这个问题，所以只有窗口这一道。
 * </p>
 * <p>
 * <b>失败方向是安全的</b>：窗口依赖「相机变换早于实体渲染」这一顺序；万一被别的模组打乱，
 * 结果是<b>火焰不显示</b>，而不是崩溃或花屏。<b>排查「火焰突然不见了」时，窗口是第一个该看的地方。</b>
 * </p>
 *
 * <h4>兜底熔断</h4>
 * <p>
 * 整合包环境里总有想不到的组合，因此 {@link #renderFlameGuarded} 再包一层：渲染抛异常时计数，
 * 累计到 {@link #MAX_RENDER_FAILURES} 次就<b>永久停用</b>火焰渲染并记一条日志。
 * <b>刻意不是「一次异常就停用」</b>——偶发的瞬时异常（例如材质图集正在重载）不该让功能永久消失；
 * 也<b>刻意不每次都打完整堆栈</b>——只有第一次打，之后静默计数，否则渲染热路径上的日志刷屏本身就会把游戏卡死。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public class ClientSyncFlameRenderer {

    // ==================== 火焰渲染参数（集中管理，便于手动调整） ====================

    /** 亮度闪烁幅度（0~1，越大明暗变化越明显；设为 0 则火焰亮度完全静止） */
    private static final float FLICKER_AMPLITUDE = 0.08F;
    /** 亮度闪烁速度 */
    private static final float FLICKER_SPEED = 0.60F;

    /**
     * 距离裁剪（格）：相机太远的着火实体本帧不绘制火焰。
     * <p>
     * 与其余世界渲染器取同值（48），保持一致。原版本身对实体渲染已有视锥 + 渲染距离裁剪，
     * 本裁剪的主要意义是给 {@link VisualLod#detail} 提供一个明确的距离上界。
     * </p>
     */
    private static final double CULL = 48.0;

    /** {@link #CULL} 的平方（避免开方） */
    private static final double CULL_SQR = CULL * CULL;

    /** 火焰分层的基准步长（格，缩放空间内） */
    private static final float FLAME_LAYER_STEP = 0.45F;

    /** 每层火焰的横向收缩比例 */
    private static final float FLAME_LAYER_SHRINK = 0.9F;

    /** 每层火焰的纵深推进量 */
    private static final float FLAME_LAYER_Z_ADVANCE = 0.03F;

    /**
     * 层步长缩放的细节系数下限。
     * <p>
     * 实际步长为 {@code FLAME_LAYER_STEP / max(本值, detail)}：细节系数越低步长越大、层数越少，
     * 但最多只放大到 {@code 1 / 本值 ≈ 1.8} 倍（层数约减半）。刻意留得很保守：
     * 火焰总共才 5 层左右，砍到 2 层会明显看出是几片分离的贴图而不是一团火。
     * </p>
     */
    private static final float FLAME_STEP_DETAIL_FLOOR = 0.55F;

    /**
     * 单帧补报实例数的上限（防御性）：兜住世界渲染被异常中断导致的累积，
     * 256 远大于任何合理的同屏着火实体数。
     */
    private static final int MAX_REPORT_PER_FRAME = 256;

    /** 兜底熔断阈值：火焰渲染累计抛出这么多次异常后<b>永久停用</b> */
    private static final int MAX_RENDER_FAILURES = 8;

    /**
     * 本帧已绘制的火焰实例数，等待补报给 {@link VisualLod}（原因见类注释「时序坑」）。
     * <p>仅客户端渲染线程访问，无并发问题。</p>
     */
    private static int pendingInstances = 0;

    /**
     * 当前是否处于「主世界实体渲染」的窗口内。
     * <p>由 {@link #onCameraSetup} 开、{@link #onRenderWorldLast} 关。仅客户端渲染线程访问。</p>
     */
    private static boolean inMainEntityPass = false;

    /** 火焰渲染累计失败次数（兜底熔断用） */
    private static int renderFailures = 0;

    /** 火焰渲染是否已被熔断永久停用；置位后两个绘制入口都会直接返回，直到游戏重启 */
    private static boolean renderDisabled = false;

    /**
     * 第一人称火焰上次画在哪一帧（{@link VisualBatch#frameId()}）。
     * <p>1.12.2 版：{@link RenderSpecificHandEvent} 主手、副手各触发一次，按帧去重，只画一次。</p>
     */
    private static int lastHandFrame = Integer.MIN_VALUE;

    /**
     * 火焰效果配置
     */
    private static class FlameConfig {
        final int serialNumber;
        /** 方块图集里的贴图名（预先拼好，避免每帧重复拼字符串） */
        final String layer0;
        final String layer1;

        /**
         * 是否在第一人称（手部渲染）中绘制本种火焰（v4 新增）。
         * <p>
         * {@code false} 表示该火焰<b>只有别人和第三人称视角能看到</b>，
         * 玩家自己在第一人称下视野干净。详见类注释「v4」一节。
         * </p>
         */
        final boolean renderInFirstPerson;

        /** 三参构造：默认<b>第一人称也绘制</b> */
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
            this.layer0 = layer0;
            this.layer1 = layer1;
            this.renderInFirstPerson = renderInFirstPerson;
        }
    }

    /**
     * 三种火焰配置。
     * <p>1.12.2 版：方块贴图在 {@code textures/blocks/} 下，图集里的名字是 {@code carianstyle:blocks/...}
     * （三种火焰方块的模型引用了它们，所以都已拼进方块图集）。</p>
     */
    private static final FlameConfig[] FLAME_CONFIGS = {
            // 注定死亡火焰（猩红色）- 序列号1
            new FlameConfig(1,
                    Reference.MOD_ID + ":blocks/crimson_flame_layer_0",
                    Reference.MOD_ID + ":blocks/crimson_flame_layer_1"),

            // 毁灭火焰（白色）- 序列号2
            // v4：第三人称专属。黑焰仪式在满足条件时会持续刷新这团火，
            //    贴在镜头前会长时间挡住视野（详见类注释「v4」一节）。
            new FlameConfig(2,
                    Reference.MOD_ID + ":blocks/white_flame_layer_0",
                    Reference.MOD_ID + ":blocks/white_flame_layer_1",
                    false),

            // 癫痫火焰（黄色）- 序列号3
            new FlameConfig(3,
                    Reference.MOD_ID + ":blocks/yellow_flame_layer_0",
                    Reference.MOD_ID + ":blocks/yellow_flame_layer_1")
    };

    /**
     * 相机变换回调：主实体渲染窗口开窗。
     * <p>
     * 1.12.2 版：它在 {@code renderWorldPass} 开头的 {@code setupCameraTransform → orientCamera} 里触发，
     * 早于 {@code RenderGlobal.renderEntities}；界面里的实体预览不走这里。
     * </p>
     *
     * @param event 相机变换事件
     */
    @SubscribeEvent
    public static void onCameraSetup(@Nonnull EntityViewRenderEvent.CameraSetup event) {
        inMainEntityPass = true;
    }

    /**
     * 世界渲染末尾回调：关窗，并补报本帧的火焰实例数。
     * <p>
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
     * @param event 世界渲染末尾事件
     */
    @SubscribeEvent
    public static void onRenderWorldLast(@Nonnull RenderWorldLastEvent event) {
        // 实体渲染已经结束：关窗
        inMainEntityPass = false;

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
     * 1.12.2 版：事件给的 {@code getX/Y/Z} 是实体脚下相对渲染原点的坐标；距离按真正的相机算
     * （渲染原点 + {@link ActiveRenderInfo#getCameraPosition()}，后者本帧在实体渲染前已经算好），
     * 和 1.20.1 用相机位置一致。
     * </p>
     *
     * @param event 实体渲染后事件
     */
    @SubscribeEvent
    public static void onRenderLiving(@Nonnull RenderLivingEvent.Post<?> event) {
        if (renderDisabled || !ClientVisualConfig.enabled || !ClientVisualConfig.flamesEnabled) {
            return;
        }
        // 必须处于主实体渲染的窗口内：界面里的实体预览、世界渲染之外的二次渲染都落在窗口之外
        if (!inMainEntityPass) {
            return;
        }

        EntityLivingBase entity = event.getEntity();
        int entityId = entity.getEntityId();
        // 帧间插值系数，用于保证亮度闪烁逐帧平滑
        float partialTick = event.getPartialRenderTick();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, entityId)) {
                // 距离裁剪 + 细节系数
                Vec3d camOffset = ActiveRenderInfo.getCameraPosition();
                double dx = event.getX() - camOffset.x;
                double dy = event.getY() - camOffset.y;
                double dz = event.getZ() - camOffset.z;
                double distSqr = dx * dx + dy * dy + dz * dz;
                if (distSqr > CULL_SQR) {
                    // 太远：整层跳过（连实例也不登记——它对同屏拥挤度确实没有贡献）
                    break;
                }
                float detail = VisualLod.detail(distSqr);

                // 攒着，不能在这里直接 countInstance（会被 beginFrame 清零）
                pendingInstances++;

                renderFlameGuarded(
                        entity,
                        event.getX(),
                        event.getY(),
                        event.getZ(),
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
     * 带兜底熔断的火焰渲染包装。
     * <p>
     * 渲染抛异常时计数，累计到 {@link #MAX_RENDER_FAILURES} 次就<b>永久停用</b>火焰渲染。
     * 只有第一次打完整堆栈（渲染热路径上日志刷屏本身就会把游戏卡死），熔断时再记一条结论。
     * </p>
     *
     * @param entity        目标实体
     * @param x             实体脚下相对渲染原点的 X
     * @param y             实体脚下相对渲染原点的 Y
     * @param z             实体脚下相对渲染原点的 Z
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     * @param partialTick   帧间插值系数
     * @param detail        本帧细节系数（1.0 为满细节）
     */
    private static void renderFlameGuarded(@Nonnull EntityLivingBase entity,
                                           double x, double y, double z,
                                           @Nonnull String layer0Texture,
                                           @Nonnull String layer1Texture,
                                           float partialTick,
                                           float detail) {
        try {
            renderEntityOnFire(entity, x, y, z, layer0Texture, layer1Texture, partialTick, detail);
        } catch (Exception | LinkageError e) {
            recordFailure("自定义火焰", e);
        }
    }

    /**
     * 记一次渲染失败（第三人称与第一人称共用同一套熔断计数：同源问题不该被拆成两份统计）。
     *
     * @param what 哪一路渲染
     * @param e    抛出的异常
     */
    private static void recordFailure(@Nonnull String what, @Nonnull Throwable e) {
        renderFailures++;
        if (renderFailures == 1) {
            // 首次：记完整堆栈用于定位（之后静默，避免渲染热路径日志刷屏）
            LogUtil.error("[CarianStyle] " + what + "渲染失败，已记录本次堆栈；后续失败将静默计数，累计 "
                    + MAX_RENDER_FAILURES + " 次后停用该效果。", e);
        }
        if (renderFailures >= MAX_RENDER_FAILURES) {
            renderDisabled = true;
            LogUtil.error("[CarianStyle] 自定义火焰渲染累计失败 %d 次，已停用（重启游戏后恢复）。"
                    + "通常意味着有其它模组在实体渲染流程中做了本模组未预期的重入渲染。", MAX_RENDER_FAILURES);
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
     * <b>不做窗口检查</b>：手部渲染（{@code EntityRenderer.renderHand}）在 {@link RenderWorldLastEvent}
     * 之后，窗口此时必然是关的。
     * </p>
     * <p>
     * 1.12.2 版：这个事件主手、副手各触发一次（触发时模型视图矩阵已经带着摇晃、受伤抖动和手臂随视角的偏摆，
     * 和 1.20.1 {@code RenderHandEvent} 的姿态相当），按帧只画一次。
     * 不取消事件，手照常画；火焰不做深度测试，永远盖在手上。
     * </p>
     *
     * @param event 手部渲染事件
     */
    @SubscribeEvent
    public static void onRenderHand(@Nonnull RenderSpecificHandEvent event) {
        if (renderDisabled || !ClientVisualConfig.enabled || !ClientVisualConfig.flamesEnabled) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        int frame = VisualBatch.frameId();
        if (frame == lastHandFrame) {
            return;
        }
        lastHandFrame = frame;

        int playerId = player.getEntityId();
        // 连续动画时间：玩家存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = player.ticksExisted + event.getPartialTicks();

        // 检查每种火焰效果
        for (FlameConfig config : FLAME_CONFIGS) {
            if (ClientSyncEffectManager.shouldRenderEffect(config.serialNumber, playerId)) {
                // v4：第三人称专属的火焰在这里止步。
                // 用 break 而不是 continue —— 第三人称那边同样是「命中第一个就 break」，
                // 若这里改用 continue 往下找，会出现「第三人称烧白焰、第一人称烧黄焰」
                // 这种两个视角不一致的情况。跳过就是彻底不画，不找替补。
                if (!config.renderInFirstPerson || !ClientVisualConfig.firstPersonFlames) {
                    break;
                }
                try {
                    renderFireInFirstPerson(config.layer1, time);
                } catch (Exception | LinkageError e) {
                    recordFailure("第一人称火焰", e);
                }
                // 只渲染第一个匹配的火焰
                break;
            }
        }
    }

    /**
     * 渲染实体身上的火焰效果
     * <p>
     * {@code detail} 控制分层步长——细节系数越低、步长越大、层数越少。
     * 火焰的<b>总高度不变</b>，变的只是这段高度被切成几片。横向收缩比与纵深推进量按步长同比换算
     * （{@code pow(0.9, step/0.45)}、{@code 0.03 × step/0.45}），使削减后火焰的整体轮廓与全细节时保持近似；
     * {@code detail = 1} 时三者恰为 0.45 / 0.9 / 0.03，与优化前逐像素一致。
     * </p>
     * <p>
     * 1.12.2 版：Post 触发时已经不在实体变换里，先 {@code translate(x, y, z)} 到实体脚下，
     * 再按 1.20.1 的顺序缩放、朝向相机、往后推一点；GL 状态由 {@link GlPresets#FLAME} 包住。
     * </p>
     *
     * @param entity        目标实体
     * @param x             实体脚下相对渲染原点的 X
     * @param y             实体脚下相对渲染原点的 Y
     * @param z             实体脚下相对渲染原点的 Z
     * @param layer0Texture 火焰纹理层0
     * @param layer1Texture 火焰纹理层1
     * @param partialTick   帧间插值系数
     * @param detail        本帧细节系数（1.0 为满细节）
     */
    private static void renderEntityOnFire(@Nonnull EntityLivingBase entity,
                                           double x, double y, double z,
                                           @Nonnull String layer0Texture,
                                           @Nonnull String layer1Texture,
                                           float partialTick,
                                           float detail) {
        Minecraft minecraft = Minecraft.getMinecraft();
        TextureMap atlas = minecraft.getTextureMapBlocks();

        TextureAtlasSprite fireLayer0 = atlas.getAtlasSprite(layer0Texture);
        TextureAtlasSprite fireLayer1 = atlas.getAtlasSprite(layer1Texture);

        // 连续动画时间：实体存活tick + 帧间插值（仅服务于亮度闪烁）
        float time = entity.ticksExisted + partialTick;
        // 每个实体使用独立相位，使多个火焰的亮度闪烁不完全同步
        float phase = entity.getEntityId() * 0.6F;

        // 固定缩放，不做呼吸脉动
        float scale = entity.width * 1.4F;
        // 火焰起始高度（缩放空间内）
        float startHeight = entity.height / scale;

        float renderX = 0.5F;
        float renderY = 0.0F;
        float renderZ = 0.0F;
        float height = startHeight;
        int stage = 0;

        // 按细节系数放大分层步长（层数变少、总高度不变），横向收缩与纵深推进同比换算。
        // 三者在循环外算一次即可——放在循环里会让每层多一次 Math.pow
        float step = FLAME_LAYER_STEP / Math.max(FLAME_STEP_DETAIL_FLOOR, detail);
        float stepRatio = step / FLAME_LAYER_STEP;
        float shrink = (float) Math.pow(FLAME_LAYER_SHRINK, stepRatio);
        float zAdvance = FLAME_LAYER_Z_ADVANCE * stepRatio;

        GlStateSnapshot snapshot = GlPresets.FLAME.enter();
        GlStateManager.pushMatrix();
        try {
            GlStateManager.translate((float) x, (float) y, (float) z);
            GlStateManager.scale(scale, scale, scale);
            // billboard：火焰始终正面朝向摄像机（不叠加任何摆动）。
            // playerViewY 在第三人称正面视角下已经加过 180°，和 1.20.1 相机的 yRot 同义
            GlStateManager.rotate(-minecraft.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0F, 0.0F, -0.3F + (float) ((int) startHeight) * 0.02F);

            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder builder = tessellator.getBuffer();
            builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

            while (height > 0.0F) {
                TextureAtlasSprite sprite = (stage % 2 == 0) ? fireLayer0 : fireLayer1;

                float minU = sprite.getMinU();
                float minV = sprite.getMinV();
                float maxU = sprite.getMaxU();
                float maxV = sprite.getMaxV();

                if (stage / 2 % 2 == 0) {
                    float temp = maxU;
                    maxU = minU;
                    minU = temp;
                }

                // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
                float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                        * MathHelper.sin(time * FLICKER_SPEED + stage * 0.7F + phase);
                int rgb = clampColor((int) (255.0F * flicker));

                float left = -renderX;
                float right = renderX;
                float bottomY = -renderY;
                float topY = 1.4F - renderY;

                // 底部右、底部左、顶部左、顶部右（alpha 固定 255，完全不透明）
                builder.pos(right, bottomY, renderZ).tex(maxU, maxV).color(rgb, rgb, rgb, 255).endVertex();
                builder.pos(left, bottomY, renderZ).tex(minU, maxV).color(rgb, rgb, rgb, 255).endVertex();
                builder.pos(left, topY, renderZ).tex(minU, minV).color(rgb, rgb, rgb, 255).endVertex();
                builder.pos(right, topY, renderZ).tex(maxU, minV).color(rgb, rgb, rgb, 255).endVertex();

                height -= step;
                renderY -= step;
                renderX *= shrink;
                renderZ += zAdvance;
                stage++;
            }

            tessellator.draw();
        } finally {
            GlStateManager.popMatrix();
            snapshot.restore();
        }
    }

    /**
     * 渲染第一人称火焰效果
     * <p>
     * 1.12.2 版：画法照第一期（和原版第一人称着火的两片火一样的位置），外加 1.20.1 的
     * {@code firstPersonFlameX/Y/Scale} 显示调整；GL 状态由 {@link GlPresets#FLAME_FIRST_PERSON} 包住
     * （深度测试 ALWAYS、不写深度，永远画在手上）。
     * </p>
     *
     * @param textureLocation 火焰纹理（方块图集里的名字）
     * @param time            连续动画时间（玩家tick + 帧间插值）
     */
    private static void renderFireInFirstPerson(@Nonnull String textureLocation, float time) {
        Minecraft minecraft = Minecraft.getMinecraft();

        TextureAtlasSprite sprite = minecraft.getTextureMapBlocks().getAtlasSprite(textureLocation);

        float minU = sprite.getMinU();
        float maxU = sprite.getMaxU();
        float minV = sprite.getMinV();
        float maxV = sprite.getMaxV();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();

        GlStateSnapshot snapshot = GlPresets.FLAME_FIRST_PERSON.enter();
        try {
            for (int i = 0; i < 2; i++) {
                // 左右两片火焰使用错开的相位，使亮度闪烁不完全同步
                float sidePhase = i * 2.5F;

                GlStateManager.pushMatrix();
                try {
                    // 第一人称整体位置与大小，只改变显示，不改变实体或伤害范围。
                    GlStateManager.translate(ClientVisualConfig.firstPersonFlameX, ClientVisualConfig.firstPersonFlameY, 0.0F);
                    GlStateManager.scale(ClientVisualConfig.firstPersonFlameScale,
                            ClientVisualConfig.firstPersonFlameScale, 1.0F);
                    GlStateManager.translate((float) (-(i * 2 - 1)) * 0.24F, -0.3F, 0.0F);
                    GlStateManager.rotate((float) (i * 2 - 1) * 10.0F, 0.0F, 1.0F, 0.0F);

                    // 亮度闪烁（仅调制顶点 RGB，不改色相，不改透明度）
                    float flicker = (1.0F - FLICKER_AMPLITUDE) + FLICKER_AMPLITUDE
                            * MathHelper.sin(time * FLICKER_SPEED + sidePhase);
                    int rgb = clampColor((int) (255.0F * flicker));

                    builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
                    // 底部左、底部右、顶部右、顶部左（alpha 固定 255，完全不透明）
                    builder.pos(-0.5D, -0.5D, -0.5D).tex(maxU, maxV).color(rgb, rgb, rgb, 255).endVertex();
                    builder.pos(0.5D, -0.5D, -0.5D).tex(minU, maxV).color(rgb, rgb, rgb, 255).endVertex();
                    builder.pos(0.5D, 0.5D, -0.5D).tex(minU, minV).color(rgb, rgb, rgb, 255).endVertex();
                    builder.pos(-0.5D, 0.5D, -0.5D).tex(maxU, minV).color(rgb, rgb, rgb, 255).endVertex();
                    tessellator.draw();
                } finally {
                    GlStateManager.popMatrix();
                }
            }
        } finally {
            snapshot.restore();
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
