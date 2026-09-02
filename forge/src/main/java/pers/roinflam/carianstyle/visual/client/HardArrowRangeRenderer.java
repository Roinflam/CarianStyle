package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 硬箭「12 格参考距离」指示圈（纯客户端，<b>只有持有者自己看得见</b>）。
 * <p>
 * {@code EnchantmentHardArrow} 的机制是「弓箭伤害 +[等级]×80%，但 12 格内有生物时
 * 自身受到的伤害 +[等级]×80%」。12 格远超肉眼可靠估距的范围，
 * 玩家没法判断「那只僵尸到底进没进圈」。本渲染器就是给这个距离画一把尺子。
 * </p>
 *
 * <h3>它<b>只是</b>一把尺子</h3>
 * <p>
 * <b>本类不做任何实体扫描，也不判断范围内有没有东西。</b>它只画一个固定 12 格的圈，
 * 剩下的交给玩家自己看。这是刻意的设计取舍：
 * </p>
 * <ul>
 *     <li><b>零运行时开销。</b>没有 AABB 查询、没有结果列表分配、没有周期性扫描任务。
 *         唯一的周期性工作是每 {@value #LEVEL_CHECK_INTERVAL_TICKS} tick 读一次主手物品的附魔等级；</li>
 *     <li><b>不会说谎。</b>{@code EnchantmentHardArrow} 走的是
 *         {@code EntityUtil.getNearbyEntities}，其内部是 AABB 盒形还是球形距离尚未确认。
 *         若本类自己扫一遍再染色，一旦形状口径不一致，圈的边缘就会出现
 *         「明明是绿的却已经中招」——那比不提示更糟；</li>
 *     <li><b>不广播弱点。</b>只对 {@code mc.player} 绘制。硬箭的 12 格是持有者自己的风险，
 *         画给别人看等于告诉对手「站进这里能让我多吃 80% 伤害」。</li>
 * </ul>
 *
 * <h3>v3.0：把「看不清」修掉</h3>
 * <p>
 * v2.0 为了不干扰视野把亮度压到 0.2、用了 {@code 0x8FB4C0} 这种低饱和灰青、
 * 环带只有 0.16 格宽。结果是<b>压过头了</b>——在草地、沙地、雪地上几乎看不出来，
 * 一把看不见的尺子等于没有尺子。
 * </p>
 * <p>
 * 这一版换了个思路：<b>不靠提亮，靠形状</b>。
 * </p>
 * <ul>
 *     <li><b>实线改虚线</b>（{@value #DASH_COUNT} 段）。虚线是人眼识别「测量标记」最强的信号——
 *         地图、图纸、瞄准具全都用它。同样的亮度下，断续的线比连续的线<b>更容易被认出是人造标记</b>，
 *         而不是被当成地面纹理的一部分；</li>
 *     <li><b>亮度 0.2 → {@value #ALPHA}，环带 0.16 → {@value #RING_HALF_WIDTH} 格。</b>
 *         虚线本身占的像素比实线少，所以即使单段更亮更粗，整体的视觉重量反而没怎么增加；</li>
 *     <li><b>配色换成明确的青色</b> {@code 0x5FD8E8}。原来那个灰青在灰白色地面上是隐形的；
 *         青色在 Minecraft 的地形色里几乎不存在（草绿、土棕、石灰、沙黄都不含它），
 *         因此不管站在哪种地面上都能分辨；</li>
 *     <li><b>四个方位加长刻度</b>。除了 {@value #TICK_COUNT} 道常规向内刻度，
 *         正前后左右四个方向的刻度加长到 {@value #CARDINAL_TICK_LENGTH} 格，
 *         让玩家一眼看出圈的朝向与自己的关系。</li>
 * </ul>
 * <p>
 * <b>依然没有任何脉动、呼吸或颜色变化。</b>这是常驻元素，动起来就会变成视野污染——
 * 提高可见度的正确做法是改形状和配色，不是让它闪。
 * </p>
 *
 * <h3>v3.1：进出过渡</h3>
 * <p>
 * v3.0 的圈是<b>硬切</b>的：换上弓的那一帧整个圈直接出现，收起时又直接消失。
 * 一个 12 格宽的图形突然出现在脚下，观感上就是「闪了一下」，
 * 而且会让人误以为刚才发生了什么事件。
 * </p>
 * <p>
 * 现在加了 {@value #TRANSITION_TICKS} tick 的展开 / 收起动画，
 * <b>三个量同时被驱动</b>：透明度淡入、半径从 {@value #TRANSITION_START_SCALE}
 * 倍展开到满尺寸、虚线整体转过 {@value #TRANSITION_SPIN_SECTORS} 个扇区归位。
 * </p>
 * <p>
 * 三者缺一不可：只做透明度会读成「一张贴图在变淡」；
 * 加上半径变化才有「展开」的感觉；而那一下轻微的旋转是最点睛的——
 * 它让整个动作像<b>一个仪器在对焦归位</b>，而不是一个技能特效在生效。
 * 这正是本类想给人的印象：它是工具，不是特效。
 * </p>
 * <p>
 * <b>注意这与「无脉动」并不矛盾。</b>过渡只在<b>状态切换的那 0.4 秒</b>发生，
 * 展开完成后就是完全静止的——常驻期间依然一动不动。
 * </p>
 *
 * <h3>为什么不接进既有的光环系统</h3>
 * <p>
 * 项目里已有 {@code AuraDisplayRegistry} + {@code AuraScanner} + {@code AuraGroundRenderer}
 * 这套完整链路，但它的设计前提是「谁身上有这个附魔，谁脚下就画一个圈，<b>所有人都看得见</b>」——
 * 这对魔法之境、圣域这类<b>影响他人</b>的光环是对的，对本需求则正好相反。
 * </p>
 * <p>
 * 要在那套系统里加「只对自己可见」，得同时改条目结构、扫描逻辑与约一千五百行的绘制分发。
 * 而本类完全自包含：<b>只读 {@code mc.player} 的主手物品，零网络包、零服务端配合</b>，
 * 那三个文件一行都不用改。
 * </p>
 *
 * <h3>口径核对结果</h3>
 * <p>已按 {@code EnchantmentHardArrow} 源码逐条核对：</p>
 * <ul>
 *     <li><b>范围 12 格</b>——与 {@link #RANGE} 一致 ✓</li>
 *     <li><b>注册名 {@code hard_arrow}</b>——取自 {@code @AutoRegisterEnchantment} ✓</li>
 *     <li><b>只认主手</b>——两个代价入口（{@code onLivingKnockBack} 与
 *         {@code onHurtAsVictimHighest}）都只读 {@code MAIN_HAND}，
 *         注册的 {@code slots} 也只有 {@code MAINHAND}。
 *         故 {@link #currentLevel} 同样只查主手：把弓换到副手代价根本不触发，
 *         此时还画着圈就是在提示一个不存在的风险 ✓</li>
 * </ul>
 *
 * @author FlameForge
 * @version 3.1
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class HardArrowRangeRenderer {

    /** 硬箭附魔的注册名（取自 {@code @AutoRegisterEnchantment(id = "hard_arrow")}） */
    private static final String HARD_ARROW_ID = "hard_arrow";

    /**
     * 参考距离（格）。
     * <p>与 {@code EnchantmentHardArrow} 两处
     * {@code EntityUtil.getNearbyEntities(..., 12, ...)} 的硬编码值一致。
     * 若附魔那边改了，这里必须同步。</p>
     */
    private static final double RANGE = 12.0;

    /**
     * 主手附魔等级的重查间隔（tick）。
     * <p>
     * 读附魔等级要反序列化物品 NBT，每帧做就是每秒 60 次，必须节流。
     * </p>
     * <p>
     * <b>v3.1 由 10 缩短到 {@value}。</b>加了进出过渡之后，这个间隔就成了
     * 「换上弓 → 圈开始出现」之间的<b>死等时间</b>——过渡本身再顺滑，
     * 前面先杵半秒也一样别扭。{@value} tick 是 0.25 秒，接在
     * {@value #TRANSITION_TICKS} tick 的展开动画前面已经察觉不到了。
     * </p>
     */
    private static final int LEVEL_CHECK_INTERVAL_TICKS = 5;

    /**
     * 进出过渡的时长（tick）。
     * <p>
     * {@value} tick ≈ 0.4 秒。再快就接近「啪地跳出来」、失去过渡的意义；
     * 再慢则换武器时圈会拖泥带水地跟着你，反而显得迟钝。
     * </p>
     */
    private static final int TRANSITION_TICKS = 8;

    /**
     * 展开动画的起始半径比例。
     * <p>
     * 圈从 {@value} 倍半径收拢状态展开到满尺寸。<b>刻意不从 0 开始</b>——
     * 从零展开会读作「一个东西从脚下冒出来」，像个技能特效；
     * 从八成半径微微张开则读作「尺子对上了焦」，符合它工具的定位。
     * </p>
     */
    private static final float TRANSITION_START_SCALE = 0.82f;

    /**
     * 展开过程中虚线整体旋转的扇区数。
     * <p>
     * 展开时虚线段会额外转过 {@value} 个扇区再停下，收起时反向。
     * 这一下轻微的「对准」旋转是过渡里最点睛的部分：
     * 单纯的缩放加淡入会显得是一张贴图在变透明度，加了旋转才像个仪器在归位。
     * </p>
     */
    private static final float TRANSITION_SPIN_SECTORS = 1.5f;

    /** 离地高度偏移，避免与地表 z-fighting */
    private static final float Y_OFFSET = 0.03f;
    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 虚线段数。
     * <p>{@value} 段在半径 12 格的圆上，每段弧长约 1.5 格——
     * 远看连成一圈，近看能明确分辨出是断续的标记而非实心装饰。</p>
     */
    private static final int DASH_COUNT = 32;
    /**
     * 每段虚线占其所属扇区的比例。
     * <p>{@value} 表示画 62% 留 38%。留白太少会退化成实线、失去「测量标记」的读法；
     * 留白太多则远看断成一串点，读不出圆。</p>
     */
    private static final float DASH_FILL = 0.62f;
    /** 每段虚线的细分数（保证弧段本身不显得是直线） */
    private static final int DASH_SEGMENTS = 3;

    /** 环带半宽（格）。v3.0 由 0.16 加粗到 {@value} */
    private static final float RING_HALF_WIDTH = 0.24f;
    /** 常规刻度线条数（沿圆周均布，向内） */
    private static final int TICK_COUNT = 12;
    /** 常规刻度线长度（格，自主环向内） */
    private static final float TICK_LENGTH = 0.9f;
    /** 四方位加长刻度的长度（格），让玩家一眼读出圈与自身朝向的关系 */
    private static final float CARDINAL_TICK_LENGTH = 1.9f;
    /** 刻度线半宽（格） */
    private static final float TICK_HALF_WIDTH = 0.1f;

    /**
     * 不透明度。<b>常量，无脉动。</b>
     * <p>v3.0 由 0.2 提到 {@value}。配合虚线化，实际占的像素比 v2.0 的实线圈还少一些，
     * 但辨识度高得多。</p>
     */
    private static final float ALPHA = 0.42f;

    /**
     * 配色：明确的青色。
     * <p>
     * v3.0 由 {@code 0x8FB4C0}（低饱和灰青）换成 {@value}。
     * 选青色的理由很实际：<b>Minecraft 的地形色里几乎没有青</b>——
     * 草绿、土棕、石灰、沙黄、雪白都不含它，所以不管站在哪种地面上都能分辨。
     * 原来那个灰青在石头和雪地上基本是隐形的。
     * </p>
     * <p>同时它<b>不属于任何附魔的配色体系</b>，不会被误读成某个增益或危险提示。</p>
     */
    private static final int COLOR_REFERENCE = 0x5FD8E8;

    /** ⚠ C_ 前缀：类加载时解包一次的常量配色，只读，切勿作为写入目标 */
    private static final float[] C_REFERENCE = VisualColor.constant(COLOR_REFERENCE);

    /**
     * 懒解析出的硬箭附魔实例。
     * <p>附魔注册表在类加载时还没建好，因此不能作为静态初始化；
     * 首次使用时解析一次并缓存（与 {@code CalamityRenderer} 的做法一致）。</p>
     */
    private static Enchantment hardArrow;
    /** 是否已尝试过解析（用于区分「还没解析」与「解析过但注册表里没有」） */
    private static boolean resolved;

    /** 距下次重查等级剩余的 tick 数 */
    private static int checkCooldown;
    /** 上次查到的主手硬箭等级（0 表示主手没拿带硬箭的弓） */
    private static int levelCached;

    /**
     * 上一 tick 的过渡进度，用于与 {@link #transitionCur} 做 partialTick 插值。
     * <p>没有这一份的话，8 tick 的过渡在 60fps 下会明显看出是八级台阶。</p>
     */
    private static float transitionPrev;
    /** 当前过渡进度（0 = 完全收起，1 = 完全展开） */
    private static float transitionCur;

    private HardArrowRangeRenderer() {
    }

    /**
     * 客户端 tick：重查主手硬箭等级，并推进进出过渡。
     * <p>
     * <b>这两件事的节奏必须分开。</b>等级重查要读 NBT，贵，只能每
     * {@value #LEVEL_CHECK_INTERVAL_TICKS} tick 做一次；
     * 而过渡进度是纯浮点加减，必须<b>每 tick</b> 都走，否则动画会跟着重查间隔一卡一卡。
     * v3.0 之前的写法在等级检查的冷却分支里直接 {@code return}，
     * 若把过渡逻辑放在后面就会被这个 return 跳过——这是加过渡时最容易踩的坑。
     * </p>
     * <p>离开世界时全部清零，避免重进世界的第一帧沿用上一局的状态、
     * 或者带着一个半展开的圈突然出现。</p>
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            levelCached = 0;
            checkCooldown = 0;
            transitionPrev = 0f;
            transitionCur = 0f;
            return;
        }

        // ===== 贵的那件事：节流重查 =====
        if (checkCooldown > 0) {
            checkCooldown--;
        } else {
            checkCooldown = LEVEL_CHECK_INTERVAL_TICKS;
            levelCached = currentLevel(player);
        }

        // ===== 廉价的那件事：每 tick 推进过渡 =====
        transitionPrev = transitionCur;
        float target = levelCached > 0 ? 1f : 0f;
        float step = 1f / TRANSITION_TICKS;
        if (transitionCur < target) {
            transitionCur = Math.min(target, transitionCur + step);
        } else if (transitionCur > target) {
            transitionCur = Math.max(target, transitionCur - step);
        }
    }

    /**
     * 取当前<b>主手</b>硬箭附魔的等级。
     * <p>
     * <b>只查主手，不查副手</b>——这不是疏漏，而是与附魔实现严格对齐：
     * {@code EnchantmentHardArrow} 的两个代价入口都只读 {@code MAIN_HAND}，
     * 注册 {@code slots} 也只有 {@code MAINHAND}。把弓拿到副手代价不会触发，
     * 此时还画着参考圈就是在提示一个并不存在的风险。
     * </p>
     *
     * @param player 本地玩家
     * @return 硬箭等级；主手未持有则为 0
     */
    private static int currentLevel(LocalPlayer player) {
        Enchantment ench = resolveHardArrow();
        if (ench == null) {
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty() || !stack.isEnchanted()) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ench, stack);
    }

    /**
     * 懒解析硬箭附魔实例（只解析一次，含失败缓存）。
     *
     * @return 附魔实例；注册表里没有则返回 null
     */
    private static Enchantment resolveHardArrow() {
        if (!resolved) {
            resolved = true;
            hardArrow = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, HARD_ARROW_ID));
        }
        return hardArrow;
    }

    /**
     * 渲染回调：只为本地玩家画一圈固定 12 格的虚线参考圈。
     * <p>顶点量恒定：虚线 {@value #DASH_COUNT}×{@value #DASH_SEGMENTS}×6
     * + 刻度 {@value #TICK_COUNT}×6 ≈ 650，且同屏最多只有一个实例
     * （只画本地玩家），无需 LOD 分级。</p>
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        // ⚠ 判据是过渡值而不是 levelCached：收弓之后还要把收起动画播完
        if (transitionCur <= 0f && transitionPrev <= 0f) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }
        Matrix4f matrix = VisualBatch.matrix();
        float partial = event.getPartialTick();

        // 过渡进度：先在两个 tick 之间插值，再套缓出。
        // 缓出让展开一开始就快、末尾轻轻收住，比线性自然得多
        float raw = Mth.lerp(partial, transitionPrev, transitionCur);
        if (raw <= 0.002f) {
            return;
        }
        float t = easeOutCubic(raw);

        // 登记一个绘制实例。本渲染器同样往共享批次写顶点，
        // 漏登记会让全局 crowdFactor 被低估、别的重量级渲染器削减不足
        VisualLod.countInstance();

        // 用插值坐标，避免高速移动时参考圈相对玩家抖动
        double px = Mth.lerp(partial, player.xo, player.getX());
        double py = Mth.lerp(partial, player.yo, player.getY());
        double pz = Mth.lerp(partial, player.zo, player.getZ());

        float cx = (float) (px - cam.x);
        float cy = (float) (py - cam.y) + Y_OFFSET;
        float cz = (float) (pz - cam.z);

        // 三个量一起被过渡驱动：透明度淡入、半径由收拢展开、虚线旋转归位。
        // 只做透明度会读成「一张贴图在变淡」，三者叠加才像个仪器在对焦
        float alpha = ALPHA * t;
        float radius = (float) RANGE * (TRANSITION_START_SCALE
                + (1f - TRANSITION_START_SCALE) * t);
        float dashRot = (1f - t) * (TAU / DASH_COUNT) * TRANSITION_SPIN_SECTORS;

        // ===== 虚线主圈：形状本身就是「这是一个测量标记」的信号 =====
        dashedRing(builder, matrix, cx, cy, cz, radius,
                RING_HALF_WIDTH, dashRot, C_REFERENCE, alpha);

        // ===== 刻度线：向内的短线，四个方位加长 =====
        float outerR = radius - RING_HALF_WIDTH;
        for (int i = 0; i < TICK_COUNT; i++) {
            float ang = dashRot + TAU * i / TICK_COUNT;
            float ca = Mth.cos(ang);
            float sa = Mth.sin(ang);
            // 每隔 TICK_COUNT/4 道加长一次，正好落在四个正方位上
            boolean cardinal = (i % (TICK_COUNT / 4)) == 0;
            // 刻度长度也跟着展开，收起时先缩回环上，避免最后一帧还支棱着几根线
            float len = (cardinal ? CARDINAL_TICK_LENGTH : TICK_LENGTH) * t;
            float innerR = outerR - len;
            line(builder, matrix,
                    cx + ca * outerR, cz + sa * outerR,
                    cx + ca * innerR, cz + sa * innerR,
                    cy, TICK_HALF_WIDTH, C_REFERENCE,
                    alpha * (cardinal ? 1f : 0.8f), 0f);
        }
    }

    /**
     * 缓出（cubic）：起步快、末尾轻轻收住。
     *
     * @param x 归一化输入
     * @return 缓出后的值
     */
    private static float easeOutCubic(float x) {
        float inv = 1f - x;
        return 1f - inv * inv * inv;
    }

    // ==================== 几何基元 ====================

    /**
     * 水平<b>虚线</b>圆环：把整圈切成 {@value #DASH_COUNT} 个扇区，每个扇区只画前
     * {@value #DASH_FILL} 的部分。
     * <p>
     * 这是 v3.0 提升可见度的主要手段。断续的线在视觉上被识别为「人造的测量标记」，
     * 而连续的细线容易被当作地面纹理的一部分忽略掉——同样的亮度下前者显眼得多。
     * </p>
     *
     * @param radius    环半径（格）
     * @param halfWidth 环带半宽（格）
     * @param rot       整圈旋转角（弧度），进出过渡时的「对准」旋转由它驱动
     * @param col       配色（只读）
     * @param alpha     不透明度
     */
    private static void dashedRing(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz, float radius,
                                   float halfWidth, float rot, float[] col, float alpha) {
        if (alpha <= 0.004f || radius <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rInner = Math.max(0f, radius - halfWidth);
        float rOuter = radius + halfWidth;
        float sector = TAU / DASH_COUNT;
        float dashSpan = sector * DASH_FILL;

        // ⭐ 每段虚线内部递推复用三角值：一段虚线的 DASH_SEGMENTS 个细分是连续的，
        // 本细分的末端就是下一细分的起点。虚线之间有间隔，故每段起点仍需重算。
        // 这样 cos/sin 调用由 DASH_COUNT×DASH_SEGMENTS×2 降到
        // DASH_COUNT×(DASH_SEGMENTS+1)。本渲染器是<b>常驻</b>的，
        // 只要举着弓就每帧都在跑，这点省量是每一帧都在省
        float step = dashSpan / DASH_SEGMENTS;
        for (int d = 0; d < DASH_COUNT; d++) {
            float start = rot + sector * d;
            float cos0 = Mth.cos(start);
            float sin0 = Mth.sin(start);
            for (int s = 0; s < DASH_SEGMENTS; s++) {
                float a1 = start + step * (s + 1);
                float cos1 = Mth.cos(a1), sin1 = Mth.sin(a1);

                float ox0 = cx + rOuter * cos0, oz0 = cz + rOuter * sin0;
                float ox1 = cx + rOuter * cos1, oz1 = cz + rOuter * sin1;
                float ix0 = cx + rInner * cos0, iz0 = cz + rInner * sin0;
                float ix1 = cx + rInner * cos1, iz1 = cz + rInner * sin1;

                b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
                b.vertex(m, ox1, cy, oz1).color(r, g, bl, alpha).endVertex();
                b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();

                b.vertex(m, ox0, cy, oz0).color(r, g, bl, alpha).endVertex();
                b.vertex(m, ix1, cy, iz1).color(r, g, bl, alpha).endVertex();
                b.vertex(m, ix0, cy, iz0).color(r, g, bl, alpha).endVertex();

                cos0 = cos1;
                sin0 = sin1;
            }
        }
    }

    /**
     * 带宽度的水平线段（两端 alpha 可不同）。
     *
     * @param x1  起点 X
     * @param z1  起点 Z
     * @param x2  终点 X
     * @param z2  终点 Z
     * @param y   高度
     * @param hw  线半宽（格）
     * @param col 配色（只读）
     * @param a1  起点端不透明度
     * @param a2  终点端不透明度
     */
    private static void line(BufferBuilder b, Matrix4f m,
                             float x1, float z1, float x2, float z2, float y,
                             double hw, float[] col, float a1, float a2) {
        if (a1 <= 0.004f && a2 <= 0.004f) {
            return;
        }
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) {
            return;
        }
        double nx = -dz / len * hw;
        double nz = dx / len * hw;
        float r = col[0], g = col[1], bl = col[2];
        float ax1 = (float) (x1 + nx), az1 = (float) (z1 + nz);
        float ax2 = (float) (x1 - nx), az2 = (float) (z1 - nz);
        float bx1 = (float) (x2 + nx), bz1 = (float) (z2 + nz);
        float bx2 = (float) (x2 - nx), bz2 = (float) (z2 - nz);

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, y, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, y, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, y, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, y, az2).color(r, g, bl, a1).endVertex();
    }
}
