package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 卡利亚式奉还「待命辉剑阵」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentCarianRetaliation}：举盾受到魔法或远程伤害时，
 * 生成 3 道魔法辉剑自动追踪射向攻击者，每道造成 [等级]×20% 魔法伤害。
 * </p>
 * <p>
 * <b>本渲染器画的是「反击尚未触发」的待命状态</b>——玩家举起盾的那一刻，
 * 盾前浮现一面卡利亚辉石法阵，三道辉剑悬在阵上缓缓公转、剑尖朝外，
 * 等着有人把魔法或箭射过来。真正打出去之后，那三道剑由
 * {@link GlintbladesEffectRenderer} 接管（它渲染的正是本附魔生成的
 * {@code EntityGlintblades} 实体），二者<b>共用同一套辉石蓝配色</b>，
 * 视觉上是连续的：待命 → 射出，同一批剑。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有两个，而<b>客户端两样都能自己读到</b>：
 * </p>
 * <ol>
 *     <li><b>正在举盾</b> —— {@code entity.isBlocking()}。原版的「正在使用物品」标志
 *         （{@code SharedFlags} 的 USING_ITEM 位）与 {@code useItem} 本身都随实体正常同步，
 *         因此观察者能准确知道别人有没有举盾；</li>
 *     <li><b>举着的那面盾带 carian_retaliation</b> —— 直接读
 *         {@code entity.getUseItem()} 的附魔 NBT。<b>用「正在使用的物品」而不是遍历双手，
 *         比检查主手 / 副手更准确</b>：举盾时 {@code getUseItem()} 返回的就是那面盾，
 *         不会把另一只手里恰好也带同名附魔的物品误判进来。</li>
 * </ol>
 * <p>
 * 这与 {@code DarkMoonRenderer} 判定暗月、{@code ScarletRotMistRenderer} 判定腐败女神
 * 是同一手法。相比之下，猩红腐败 / 冻伤 / 出血那些必须走 {@code ClientSyncEffectManager}
 * 的原因是它们基于 {@code MobEffect}——原版只在观察者<b>开始追踪</b>某实体的那一刻
 * 同步一次效果列表，战斗中途新加的效果收不到。附魔与举盾状态都没有这个问题。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentCarianRetaliation} 一行代码。
 * </p>
 *
 * <h3>形状语言：垂直于朝向的盾面法阵</h3>
 * <p>
 * 本模组现有的法阵<b>全部铺在地上</b>（因果律、冻结地震、癫火、光环、祈祷一击……），
 * 唯一的例外是 {@link GlintbladesEffectRenderer} 那个跟着辉剑走的小符文阵。
 * 本渲染器把法阵<b>立起来、垂直于持有者的朝向</b>——也就是「贴在盾面前方」，
 * 这个姿态在全模组独一份，远看就能读出「他举着盾、盾前有东西」。
 * </p>
 * <p>
 * <b>三个元素：</b>
 * </p>
 * <ol>
 *     <li><b>盾前法阵</b>（{@link #drawWardCircle}）——双环 + 六芒星 + 外缘刻度，
 *         缓慢自转。六芒星是卡利亚辉石魔法在本模组的既定符号
 *         （{@code AuraGroundRenderer.motifCarian} 与 {@code GlintbladesEffectRenderer}
 *         都用它），沿用可以让玩家一眼归类到「这是卡利亚的东西」；</li>
 *     <li><b>三道待命辉剑</b>（{@link #drawStandbyBlades}）——沿阵圆周 120° 均布，
 *         <b>剑尖朝外</b>（径向），缓慢公转并各自轻微起伏。数量刻意固定为 3，
 *         与附魔实际生成的辉剑数一致——玩家看到几把，反击时就飞出去几把；</li>
 *     <li><b>阵心辉光</b>（{@link #drawCore}）——一点小菱形柔光，作为视觉锚点。</li>
 * </ol>
 *
 * <h3>举盾 / 放盾的出入动画</h3>
 * <p>
 * <b>举起</b>用 {@code entity.getTicksUsingItem()} 驱动——这是原版自带的
 * 「已使用物品多少 tick」计数器，随实体同步，天然就是我们要的展开进度，
 * 不需要自己维护任何状态。前 {@link #APPEAR_TICKS} tick 内法阵由小到大展开、
 * 亮度淡入。
 * </p>
 * <p>
 * <b>放下</b>则必须自己记一笔：盾一放下 {@code isBlocking()} 立刻为 false，
 * 若不做处理法阵会「啪」地消失，很生硬。故用 {@link #FADE_STATE} 记录
 * 「最后一次仍在举盾的时刻与当时的姿态」，放下后原地收缩淡出
 * {@link #FADE_SECONDS} 秒。这套状态机与 {@code AuraGroundRenderer} 的
 * 光环淡出同源，但更轻——只在<b>本帧不再举盾、而上一帧还在举</b>的实体上创建条目，
 * 淡出结束即移除，稳态下 Map 恒为空。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * 单个实体每帧顶点量：
 * </p>
 * <pre>
 * 外环（24 段 × 6）                    144
 * 内环（24 段 × 6）                    144
 * 六芒星（6 条线 × 6）                  36
 * 外缘刻度（12 条 × 6）                 72
 * 三道辉剑（3 把 × 2 面 × 6 × 2 层）    72
 * 剑尖光点（3 × 12）                    36
 * 阵心辉光（12）                        12
 * ─────────────────────────────────────
 * 合计                            ~516 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 举盾是<b>持续状态</b>，团战中盾战玩家可能长时间挂着，故完整接入
 * {@link VisualLod}（含 {@link VisualLod#countInstance()}——少登记一个渲染器
 * 就会让全局 {@code crowdFactor} 被系统性高估，已接入的重量级渲染器就削减不足）。
 * </p>
 * <p>
 * <b>三个主题色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值</b>，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需 {@code SCRATCH} 缓冲；
 * 几何也全部用标量内联，无任何临时数组。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CarianRetaliationRenderer {

    /** 卡利亚式奉还附魔的注册 id（按 {@code carianstyle:<id>} 解析） */
    private static final String RETALIATION_ID = "carian_retaliation";

    /** 距离裁剪（格）。必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}，否则会漏掉实体。 */
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>动画时间必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间会完全相同、动画彻底静止。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    /**
     * 举盾展开动画时长（tick）。
     * <p>由原版的 {@code getTicksUsingItem()} 驱动，无需自维护状态（详见类注释）。
     * 6 tick(0.3 秒) 与原版盾牌的举起手感接近，不会显得拖沓。</p>
     */
    private static final float APPEAR_TICKS = 6.0f;

    /** 放盾收缩淡出时长（秒） */
    private static final float FADE_SECONDS = 0.28f;

    // ===== LOD 下限与保留阈值 =====
    /** 法阵环的最少分段数：半径仅约 0.75 格，14 段的偏离量不足 2cm */
    private static final int RING_SEGMENTS_MIN = 14;
    /** 外缘刻度层的保留阈值：细密小段，远处糊成一圈 */
    private static final float TICK_KEEP_THRESHOLD = 0.5f;
    /** 内环层的保留阈值：外环 + 六芒星已足够表达法阵，内环是层次补充 */
    private static final float INNER_RING_KEEP_THRESHOLD = 0.45f;
    /** 剑尖光点层的保留阈值：极小的装饰光点 */
    private static final float BLADE_SPARK_KEEP_THRESHOLD = 0.4f;

    // ===== 配色（0xRRGGBB）=====
    // 刻意与 GlintbladesEffectRenderer 取同一组三色——本附魔生成的正是那些辉剑，
    // 待命状态与飞行状态用同一配色，视觉上是连续的「同一批剑」。
    /** 辉石白热：剑刃核心、阵心辉光、六芒星 */
    private static final int GLINT_CORE = 0xEAF4FF;
    /** 辉石蓝：主色，法阵双环与剑身光晕 */
    private static final int GLINT_BLUE = 0x8FD2FF;
    /** 辉石深蓝：内环与剑柄侧的暗部 */
    private static final int GLINT_DEEP = 0x3A6FC0;

    // ===== 预解包的固定配色（⚠ 只读，切勿作为写入目标）=====
    // C_ 前缀是本模组约定，表示「类加载时解包一次、此后永久复用的常量颜色数组」。
    // Java 没有不可变数组，一旦被误当作 VisualColor.*Into 的 dst 传入，
    // 会永久污染该配色且之后每帧都是错的——改动这几行时务必留意。
    //
    // 本渲染器的三个主题色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
    // 因此不需要任何 SCRATCH 复用缓冲，颜色相关分配恒为 0。
    /** 辉石白热（剑刃核心 / 六芒星 / 阵心） */
    private static final float[] C_GLINT_CORE = VisualColor.constant(GLINT_CORE);
    /** 辉石蓝（法阵外环 / 剑身光晕 / 刻度） */
    private static final float[] C_GLINT_BLUE = VisualColor.constant(GLINT_BLUE);
    /** 辉石深蓝（法阵内环） */
    private static final float[] C_GLINT_DEEP = VisualColor.constant(GLINT_DEEP);

    // ===== 盾前法阵几何参数 =====
    /** 法阵中心相对实体的前方距离（格）：约在盾牌外侧一点点 */
    private static final double WARD_FORWARD = 0.72;
    /** 法阵中心高度（格，自脚底算起）：约在胸口，与举盾时盾牌的位置一致 */
    private static final double WARD_HEIGHT = 1.15;
    /** 法阵外环半径（格） */
    private static final double WARD_RADIUS = 0.75;
    /** 内环半径相对外环的比例 */
    private static final double WARD_INNER_RATIO = 0.58;
    /** 六芒星半径相对外环的比例 */
    private static final double WARD_STAR_RATIO = 0.82;
    /** 法阵环分段数 */
    private static final int WARD_SEGMENTS = 24;
    /** 法阵环线半宽（格） */
    private static final double WARD_RING_HALF = 0.022;
    /** 法阵自转速度（弧度/秒）——缓慢，表现「蓄势待发」而非「正在施法」 */
    private static final float WARD_ROT_SPEED = 0.75f;
    /** 法阵呼吸速度 */
    private static final float WARD_BREATH_SPEED = 1.6f;
    /** 法阵外环不透明度 */
    private static final float WARD_RING_ALPHA = 0.8f;
    /** 法阵内环不透明度 */
    private static final float WARD_INNER_ALPHA = 0.55f;
    /** 六芒星不透明度 */
    private static final float WARD_STAR_ALPHA = 0.7f;
    /** 外缘刻度数量 */
    private static final int WARD_TICK_COUNT = 12;
    /** 外缘刻度长度（格） */
    private static final double WARD_TICK_LENGTH = 0.12;
    private static final float WARD_TICK_ALPHA = 0.45f;

    // ===== 待命辉剑几何参数 =====
    /**
     * 待命辉剑数量。
     * <p><b>固定为 3，与附魔实际生成的辉剑数一致</b>——玩家看到几把待命，
     * 反击时就飞出去几把，视觉与机制严格对应。等级只影响每道的伤害，不影响数量，
     * 故这里也不随等级变化。</p>
     */
    private static final int BLADE_COUNT = 3;
    /** 辉剑公转半径相对法阵外环的比例 */
    private static final double BLADE_ORBIT_RATIO = 0.62;
    /** 辉剑刃长（格） */
    private static final double BLADE_LENGTH = 0.42;
    /** 辉剑刃半宽（格） */
    private static final double BLADE_HALF_WIDTH = 0.035;
    /** 辉剑公转速度（弧度/秒）——比法阵略快，形成相对运动 */
    private static final float BLADE_ORBIT_SPEED = 1.15f;
    /** 辉剑各自轻微起伏的速度 */
    private static final float BLADE_BOB_SPEED = 2.3f;
    /** 辉剑起伏幅度（格） */
    private static final double BLADE_BOB_AMOUNT = 0.05;
    private static final float BLADE_ALPHA = 0.9f;

    // ===== 阵心辉光 =====
    /** 阵心光点半尺寸（格） */
    private static final float CORE_SIZE = 0.075f;
    private static final float CORE_ALPHA = 0.75f;

    /** 卡利亚式奉还附魔懒解析缓存（注册表在 mod 加载后才可用，首次解析成功后固定） */
    private static Enchantment retaliationCache;
    /** 是否已成功解析 */
    private static boolean retaliationResolved;

    /**
     * 放盾淡出状态（按实体网络 id 索引）。
     * <p>
     * 只在「本帧不再举盾、而上一帧还在举」的实体上创建条目，淡出结束即移除，
     * 因此稳态下（没人刚放下盾）本 Map 恒为空、零开销。
     * </p>
     * <p>仅渲染线程访问，无并发问题。</p>
     */
    private static final Map<Integer, FadeState> FADE_STATE = new HashMap<>();

    /**
     * 一个正在淡出的待命阵：记录放盾瞬间的姿态，供原地收缩淡出。
     * <p>
     * <b>为什么要记世界坐标而不是每帧重新取实体位置：</b>放盾之后玩家可能立刻
     * 冲出去、或者转身——若继续跟随，法阵会跟着人跑，读起来像「盾放下了阵还在追」。
     * 定格在放盾那一刻的位置与朝向、原地收缩消失，才符合「护盾撤了」的语义。
     * </p>
     */
    private static final class FadeState {
        /** 开始淡出的时刻（秒，墙钟） */
        float startTime;
        /** 放盾瞬间的法阵中心世界坐标 */
        double x;
        double y;
        double z;
        /** 放盾瞬间的朝向（弧度，水平） */
        float yawRad;
        /** 放盾瞬间的展开进度（若盾刚举起就放下，法阵还没完全展开，淡出应从当时的大小开始） */
        float appear;
    }

    private CarianRetaliationRenderer() {
    }

    /**
     * 世界渲染回调：绘制相机附近所有「举着带卡利亚式奉还的盾」实体的待命辉剑阵。
     *
     * @param event 渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        // 离开世界：清空淡出状态，避免残留到下次进入世界后与新的实体 id 撞号
        if (mc.level == null || mc.player == null) {
            FADE_STATE.clear();
            return;
        }
        // 共享批次未开启：直接跳过
        BufferBuilder builder = VisualBatch.builder();
        if (builder == null) {
            return;
        }
        Enchantment retaliation = resolveRetaliation();
        if (retaliation == null) {
            // 附魔未注册（如被 uninstallEnchantment 配置禁用）：不绘制
            return;
        }
        Vec3 cam = VisualBatch.cameraPosition();
        if (cam == null) {
            return;
        }

        Matrix4f matrix = VisualBatch.matrix();
        float partial = VisualBatch.partialTick();
        float time = (System.currentTimeMillis() - START_MILLIS) / 1000f;

        List<LivingEntity> candidates = SharedEntityQuery.livingEntitiesNearCamera(mc, cam);

        // ===== 1) 本帧仍在举盾的实体：正常绘制，并登记以便下一帧判断「刚放下」=====
        Set<Integer> stillBlocking = new HashSet<>();

        for (LivingEntity entity : candidates) {
            if (!isRetaliationBlocking(entity, retaliation)) {
                continue;
            }

            double ex = Mth.lerp((double) partial, entity.xo, entity.getX());
            double ey = Mth.lerp((double) partial, entity.yo, entity.getY());
            double ez = Mth.lerp((double) partial, entity.zo, entity.getZ());

            // 朝向：用插值后的 yRot，转身时法阵跟着平滑转，不会一格一格跳
            float yawDeg = Mth.rotLerp(partial, entity.yRotO, entity.getYRot());
            float yawRad = yawDeg * ((float) Math.PI / 180f);

            // 法阵中心：实体前方 WARD_FORWARD 格、胸口高度。
            // MC 中 yaw=0 面向 +Z，故朝向单位向量为 (-sin, 0, cos)
            double fwdX = -Math.sin(yawRad);
            double fwdZ = Math.cos(yawRad);
            double wx = ex + fwdX * WARD_FORWARD;
            double wy = ey + WARD_HEIGHT;
            double wz = ez + fwdZ * WARD_FORWARD;

            double dx = wx - cam.x;
            double dy = wy - cam.y;
            double dz = wz - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue;
            }

            // 展开进度：直接用原版的「已使用物品 tick 数」，无需自维护状态
            float appear = easeOutCubic(Mth.clamp(
                    (entity.getTicksUsingItem() + partial) / APPEAR_TICKS, 0f, 1f));

            int id = entity.getId();
            stillBlocking.add(id);
            // 持续刷新淡出状态里的姿态快照——放下盾的那一帧刚好留下最后的正确姿态
            FadeState st = FADE_STATE.get(id);
            if (st == null) {
                st = new FadeState();
                FADE_STATE.put(id, st);
            }
            st.startTime = -1f; // 仍在举盾：清除淡出标记
            st.x = wx;
            st.y = wy;
            st.z = wz;
            st.yawRad = yawRad;
            st.appear = appear;

            float detail = VisualLod.detail(distSqr);
            VisualLod.countInstance();

            drawWard(builder, matrix,
                    (float) dx, (float) dy, (float) dz, yawRad, appear, 1f, time, id, detail);
        }

        // ===== 2) 刚放下盾的实体：原地收缩淡出 =====
        Iterator<Map.Entry<Integer, FadeState>> it = FADE_STATE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, FadeState> e = it.next();
            int id = e.getKey();
            FadeState st = e.getValue();

            if (stillBlocking.contains(id)) {
                continue; // 本帧仍在举盾，已在上面画过
            }

            if (st.startTime < 0f) {
                // 本帧刚放下：开始计时
                st.startTime = time;
            }

            float t = (time - st.startTime) / FADE_SECONDS;
            if (t >= 1f) {
                it.remove(); // 淡出结束
                continue;
            }

            float v = 1f - t;
            // 收缩到 60% 并淡出——「护盾撤了」的收势
            float shrink = st.appear * (0.6f + 0.4f * v);

            double dx = st.x - cam.x;
            double dy = st.y - cam.y;
            double dz = st.z - cam.z;
            double distSqr = dx * dx + dy * dy + dz * dz;
            if (distSqr > CULL_SQR) {
                continue; // 太远：本帧不画，但淡出计时继续
            }

            float detail = VisualLod.detail(distSqr);
            VisualLod.countInstance();

            drawWard(builder, matrix,
                    (float) dx, (float) dy, (float) dz, st.yawRad, shrink, v, time, id, detail);
        }
    }

    /**
     * 判断实体是否「正在举着一面带卡利亚式奉还的盾」。
     * <p>
     * <b>用 {@code getUseItem()} 而非遍历双手</b>：举盾时正在使用的物品<b>就是</b>那面盾，
     * 这样既准确（不会把另一只手里恰好也带同名附魔的物品算进来），
     * 也省掉一次槽位遍历。{@code isBlocking()} 内部已经检查了
     * 「正在使用 + 使用动作为 BLOCK」，因此不必再判物品是不是盾。
     * </p>
     * <p>
     * 用 {@link ItemStack#isEnchanted()} 做廉价前置过滤——该方法只检查 NBT 标签是否存在、
     * 不做任何反序列化，能砍掉未附魔盾牌的 {@code getItemEnchantmentLevel} 调用
     * （后者会逐条遍历附魔 NBT 并做 {@code ResourceLocation} 解析）。
     * </p>
     *
     * @param entity      待判定实体
     * @param retaliation 卡利亚式奉还附魔（非 null，调用方已判空）
     * @return 正在举着带该附魔的盾返回 true
     */
    private static boolean isRetaliationBlocking(LivingEntity entity, Enchantment retaliation) {
        if (!entity.isBlocking()) {
            return false;
        }
        ItemStack shield = entity.getUseItem();
        if (shield.isEmpty() || !shield.isEnchanted()) {
            return false;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(retaliation, shield) > 0;
    }

    /**
     * 懒解析卡利亚式奉还附魔对象（注册表在 mod 加载后才可用，故首次调用时解析并缓存）。
     * <p>仅在成功解析后才标记完成，否则下次重试——避免在注册表尚未就绪时把 null 固化下来。</p>
     *
     * @return 附魔对象；未注册（如被配置禁用）时返回 null
     */
    @Nullable
    private static Enchantment resolveRetaliation() {
        if (!retaliationResolved) {
            retaliationCache = ForgeRegistries.ENCHANTMENTS.getValue(
                    new ResourceLocation(Reference.MOD_ID, RETALIATION_ID));
            retaliationResolved = (retaliationCache != null);
        }
        return retaliationCache;
    }

    // ==================== 主绘制 ====================

    /**
     * 绘制一整面待命辉剑阵（法阵 + 三道辉剑 + 阵心辉光）。
     * <p>
     * 法阵所在平面<b>垂直于持有者朝向</b>，即「贴在盾面前方」。平面的两个基向量：
     * </p>
     * <ul>
     *     <li>{@code u} = 世界上方 (0,1,0)；</li>
     *     <li>{@code w} = 朝向向量与上方的叉积 = {@code (-fwdZ, 0, fwdX)}，
     *         即持有者的正右方。</li>
     * </ul>
     * <p>
     * 平面内极坐标 {@code (角度 a, 半径 r)} 映射到世界的公式是
     * {@code P = center + w·r·cos(a) + u·r·sin(a)}，
     * 于是 a=0 指向持有者右手边、a=90° 指向正上方。
     * </p>
     *
     * @param cx     法阵中心相对相机 X
     * @param cy     法阵中心相对相机 Y
     * @param cz     法阵中心相对相机 Z
     * @param yawRad 持有者水平朝向（弧度）
     * @param scale  尺寸系数（举起时 0→1 展开，放下时收缩）
     * @param alpha  整体不透明度系数（放下时淡出）
     * @param time   动画时间（秒，墙钟驱动）
     * @param seedId 实体网络 id（错开各实体的动画相位）
     * @param detail 本帧细节系数
     */
    private static void drawWard(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz, float yawRad,
                                 float scale, float alpha, float time, int seedId, float detail) {
        if (scale <= 0.02f || alpha <= 0.01f) {
            return;
        }

        // 朝向单位向量（MC 中 yaw=0 面向 +Z）
        float fwdX = -Mth.sin(yawRad);
        float fwdZ = Mth.cos(yawRad);
        // 平面基：u = 世界上方，w = 持有者正右方（朝向 × 上方）
        float ux = 0f, uy = 1f, uz = 0f;
        float wx = -fwdZ, wy = 0f, wz = fwdX;

        drawWardCircle(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                scale, alpha, time, seedId, detail);
        drawStandbyBlades(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                fwdX, fwdZ, scale, alpha, time, seedId, detail);
        drawCore(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz, scale, alpha, time, seedId);
    }

    /**
     * 盾前法阵：外环 + 内环 + 六芒星 + 外缘刻度，整体缓慢自转并呼吸。
     * <p>
     * <b>六芒星是卡利亚辉石魔法在本模组的既定符号</b>——
     * {@code AuraGroundRenderer.motifCarian}（魔法之境光环）与
     * {@link GlintbladesEffectRenderer}（辉剑本体的符文阵）都用它，
     * 沿用可以让玩家一眼把这面阵归类到「卡利亚体系」，而不是又一个不明来历的圈。
     * </p>
     * <p>
     * <b>削减：</b>环分段数缩放（下限 {@link #RING_SEGMENTS_MIN}）；
     * 内环与外缘刻度按保留阈值整层跳过。
     * <b>外环与六芒星无论细节多低都完整绘制</b>——前者是法阵的轮廓、
     * 后者是辨识核心，二者合计仅 180 顶点。
     * </p>
     */
    private static void drawWardCircle(BufferBuilder b, Matrix4f m,
                                       float cx, float cy, float cz,
                                       float ux, float uy, float uz,
                                       float wx, float wy, float wz,
                                       float scale, float alpha, float time, int seedId, float detail) {
        float rot = time * WARD_ROT_SPEED + seedId * 0.6f;
        float breath = 0.92f + 0.08f * Mth.sin(time * WARD_BREATH_SPEED + seedId * 0.4f);
        double radius = WARD_RADIUS * scale * breath;
        if (radius <= 0.02) {
            return;
        }
        double hw = WARD_RING_HALF * scale;
        int segments = VisualLod.scaleSegments(WARD_SEGMENTS, RING_SEGMENTS_MIN, detail);

        // 外环（法阵轮廓，不削）
        planeRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                radius - hw, radius + hw, segments, rot, C_GLINT_BLUE, WARD_RING_ALPHA * alpha, WARD_RING_ALPHA * alpha);
        // 外环辉光（向外渐隐）
        planeRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                radius + hw, radius + hw + 0.08 * scale, segments, rot,
                C_GLINT_BLUE, WARD_RING_ALPHA * 0.35f * alpha, 0f);

        // 内环（反向自转，层次补充；远处可跳过）
        if (VisualLod.keepLayer(detail, INNER_RING_KEEP_THRESHOLD)) {
            double rInner = radius * WARD_INNER_RATIO;
            planeRing(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                    rInner - hw * 0.8, rInner + hw * 0.8, segments, -rot * 1.3f,
                    C_GLINT_DEEP, WARD_INNER_ALPHA * alpha, WARD_INNER_ALPHA * alpha);
        }

        // 六芒星（两叠三角）：卡利亚辉石魔法的核心符号，不参与削减
        double rStar = radius * WARD_STAR_RATIO;
        double starHalf = hw * 0.85;
        for (int i = 0; i < 6; i++) {
            float a1 = rot + TAU * i / 6f;
            float a2 = rot + TAU * ((i + 2) % 6) / 6f;
            planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                    rStar * Mth.cos(a1), rStar * Mth.sin(a1),
                    rStar * Mth.cos(a2), rStar * Mth.sin(a2),
                    starHalf, C_GLINT_CORE, WARD_STAR_ALPHA * alpha, WARD_STAR_ALPHA * alpha);
        }

        // 外缘刻度：均布角度，必须按步长抽取（截断会让刻度只剩一段圆弧）
        if (VisualLod.keepLayer(detail, TICK_KEEP_THRESHOLD)) {
            int drawn = VisualLod.scale(WARD_TICK_COUNT, detail);
            int step = Math.max(1, WARD_TICK_COUNT / drawn);
            double rStart = radius + hw + 0.03 * scale;
            double rEnd = rStart + WARD_TICK_LENGTH * scale;
            double tickHalf = hw * 0.7;
            for (int i = 0; i < WARD_TICK_COUNT; i += step) {
                // 角度基准用原始 WARD_TICK_COUNT，保证保留刻度的方位与全细节时一致
                float a = -rot * 0.6f + TAU * i / WARD_TICK_COUNT;
                float ca = Mth.cos(a);
                float sa = Mth.sin(a);
                planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz,
                        rStart * ca, rStart * sa, rEnd * ca, rEnd * sa,
                        tickHalf, C_GLINT_BLUE, WARD_TICK_ALPHA * alpha, 0f);
            }
        }
    }

    /**
     * 三道待命辉剑：沿法阵圆周 120° 均布，<b>剑尖朝外</b>（径向），缓慢公转并各自轻微起伏。
     * <p>
     * 每把剑用「十字双面」绘制——在法阵平面内沿垂直于剑身的方向展开一个四边形，
     * 再沿<b>法阵平面法线</b>（即持有者朝向）展开另一个，
     * 于是从正面看是一道细长的光刃、从侧面看也有厚度，不会退化成一条线。
     * </p>
     * <p>
     * <b>数量固定 3 把、不随等级变化</b>——与附魔实际生成的辉剑数严格一致，
     * 玩家看到几把待命、反击时就飞出去几把。等级只影响每道的伤害。
     * </p>
     * <p>
     * <b>不参与数量削减</b>：3 把剑合计仅 72 顶点，却是「这是奉还、不是普通护盾光环」
     * 的唯一依据；且它们是 120° 均布的，减掉任何一把都会破坏对称。
     * 只有剑尖光点（纯装饰）按保留阈值整层跳过。
     * </p>
     */
    private static void drawStandbyBlades(BufferBuilder b, Matrix4f m,
                                          float cx, float cy, float cz,
                                          float ux, float uy, float uz,
                                          float wx, float wy, float wz,
                                          float fwdX, float fwdZ,
                                          float scale, float alpha, float time, int seedId, float detail) {
        float orbit = time * BLADE_ORBIT_SPEED + seedId * 0.8f;
        double orbitRadius = WARD_RADIUS * WARD_STAR_RATIO * BLADE_ORBIT_RATIO * scale;
        double bladeLen = BLADE_LENGTH * scale;
        double bladeHalf = BLADE_HALF_WIDTH * scale;
        boolean drawSpark = VisualLod.keepLayer(detail, BLADE_SPARK_KEEP_THRESHOLD);

        for (int i = 0; i < BLADE_COUNT; i++) {
            float a = orbit + TAU * i / BLADE_COUNT;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            // 各自轻微起伏：沿径向小幅进出，像悬在空中微微浮动
            double bob = Mth.sin(time * BLADE_BOB_SPEED + i * 2.1f + seedId * 0.3f) * BLADE_BOB_AMOUNT * scale;
            double rInner = orbitRadius + bob;
            double rOuter = rInner + bladeLen;

            // 剑身在平面内的两端（径向：内端为柄、外端为尖）
            double p0u = rInner * ca, p0v = rInner * sa;
            double p1u = rOuter * ca, p1v = rOuter * sa;

            // 外层辉光（更宽更淡）
            bladeQuad(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz, fwdX, fwdZ,
                    p0u, p0v, p1u, p1v, bladeHalf * 2.2, C_GLINT_BLUE,
                    0.28f * alpha, 0.05f * alpha);
            // 内层白热刃身（柄端略暗、尖端最亮）
            bladeQuad(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz, fwdX, fwdZ,
                    p0u, p0v, p1u, p1v, bladeHalf, C_GLINT_CORE,
                    BLADE_ALPHA * 0.45f * alpha, BLADE_ALPHA * alpha);

            // 剑尖光点：纯装饰，远处看不出
            if (drawSpark) {
                float tipX = cx + wx * (float) p1u + ux * (float) p1v;
                float tipY = cy + wy * (float) p1u + uy * (float) p1v;
                float tipZ = cz + wz * (float) p1u + uz * (float) p1v;
                float tw = 0.7f + 0.3f * Mth.sin(time * 5f + i * 1.7f);
                billboardDiamond(b, m, tipX, tipY, tipZ,
                        (float) (bladeHalf * 2.6 * tw), C_GLINT_CORE, 0.8f * alpha * tw);
            }
        }
    }

    /**
     * 阵心辉光：法阵正中的一点小菱形柔光，作为视觉锚点。
     * <p>仅 12 顶点，不参与削减。</p>
     */
    private static void drawCore(BufferBuilder b, Matrix4f m,
                                 float cx, float cy, float cz,
                                 float ux, float uy, float uz,
                                 float wx, float wy, float wz,
                                 float scale, float alpha, float time, int seedId) {
        float pulse = 0.75f + 0.25f * Mth.sin(time * 3.2f + seedId * 0.5f);
        billboardDiamond(b, m, cx, cy, cz, CORE_SIZE * scale * pulse,
                C_GLINT_CORE, CORE_ALPHA * alpha * pulse);
    }

    // ==================== 平面几何基元 ====================

    /**
     * 在<b>垂直于持有者朝向的平面内</b>绘制一个圆环带（annulus），内外边缘可分别指定 alpha。
     * <p>点位由平面基向量 {@code u}（上）、{@code w}（右）张成：
     * {@code P(θ) = c + w·r·cosθ + u·r·sinθ}。</p>
     *
     * @param rInner   内半径
     * @param rOuter   外半径
     * @param segments 分段数
     * @param rot      整环旋转角（弧度）
     */
    private static void planeRing(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  double rInner, double rOuter, int segments, float rot,
                                  float[] col, float alphaInner, float alphaOuter) {
        if (rOuter <= rInner || segments < 3) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float prevCos = Mth.cos(rot);
        float prevSin = Mth.sin(rot);
        for (int i = 1; i <= segments; i++) {
            float a = rot + TAU * i / segments;
            float ca = Mth.cos(a);
            float sa = Mth.sin(a);

            float ox0 = cx + (wx * prevCos + ux * prevSin) * (float) rOuter;
            float oy0 = cy + (wy * prevCos + uy * prevSin) * (float) rOuter;
            float oz0 = cz + (wz * prevCos + uz * prevSin) * (float) rOuter;
            float ox1 = cx + (wx * ca + ux * sa) * (float) rOuter;
            float oy1 = cy + (wy * ca + uy * sa) * (float) rOuter;
            float oz1 = cz + (wz * ca + uz * sa) * (float) rOuter;
            float ix0 = cx + (wx * prevCos + ux * prevSin) * (float) rInner;
            float iy0 = cy + (wy * prevCos + uy * prevSin) * (float) rInner;
            float iz0 = cz + (wz * prevCos + uz * prevSin) * (float) rInner;
            float ix1 = cx + (wx * ca + ux * sa) * (float) rInner;
            float iy1 = cy + (wy * ca + uy * sa) * (float) rInner;
            float iz1 = cz + (wz * ca + uz * sa) * (float) rInner;

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ox1, oy1, oz1).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();

            b.vertex(m, ox0, oy0, oz0).color(r, g, bl, alphaOuter).endVertex();
            b.vertex(m, ix1, iy1, iz1).color(r, g, bl, alphaInner).endVertex();
            b.vertex(m, ix0, iy0, iz0).color(r, g, bl, alphaInner).endVertex();

            // 推进到下一段：本段末端即下一段起点（漏掉这两行会让整环塌成一个扇形）
            prevCos = ca;
            prevSin = sa;
        }
    }

    /**
     * 在平面内绘制一条带宽度的线段（用平面二维坐标表达端点）。
     * <p>供六芒星与外缘刻度使用：法阵的全部图案都活在这个平面里，
     * 用二维坐标描述比逐点算三维方便得多，也不易出错。</p>
     *
     * @param px1 起点在平面内的 w 分量（右）
     * @param py1 起点在平面内的 u 分量（上）
     * @param px2 终点在平面内的 w 分量
     * @param py2 终点在平面内的 u 分量
     * @param hw  线半宽
     */
    private static void planeLine(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  double px1, double py1, double px2, double py2,
                                  double hw, float[] col, float a1, float a2) {
        double ddx = px2 - px1;
        double ddy = py2 - py1;
        double len = Math.sqrt(ddx * ddx + ddy * ddy);
        if (len < 1.0e-6) {
            return;
        }
        // 平面内的法线 × 半宽
        double nx = -ddy / len * hw;
        double ny = ddx / len * hw;

        float r = col[0], g = col[1], bl = col[2];
        // 四个角点（平面二维 → 世界三维）
        double a1u = px1 + nx, a1w = py1 + ny;
        double a2u = px1 - nx, a2w = py1 - ny;
        double b1u = px2 + nx, b1w = py2 + ny;
        double b2u = px2 - nx, b2w = py2 - ny;

        float ax1 = cx + wx * (float) a1u + ux * (float) a1w;
        float ay1 = cy + wy * (float) a1u + uy * (float) a1w;
        float az1 = cz + wz * (float) a1u + uz * (float) a1w;
        float ax2 = cx + wx * (float) a2u + ux * (float) a2w;
        float ay2 = cy + wy * (float) a2u + uy * (float) a2w;
        float az2 = cz + wz * (float) a2u + uz * (float) a2w;
        float bx1 = cx + wx * (float) b1u + ux * (float) b1w;
        float by1 = cy + wy * (float) b1u + uy * (float) b1w;
        float bz1 = cz + wz * (float) b1u + uz * (float) b1w;
        float bx2 = cx + wx * (float) b2u + ux * (float) b2w;
        float by2 = cy + wy * (float) b2u + uy * (float) b2w;
        float bz2 = cz + wz * (float) b2u + uz * (float) b2w;

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx1, by1, bz1).color(r, g, bl, a2).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();

        b.vertex(m, ax1, ay1, az1).color(r, g, bl, a1).endVertex();
        b.vertex(m, bx2, by2, bz2).color(r, g, bl, a2).endVertex();
        b.vertex(m, ax2, ay2, az2).color(r, g, bl, a1).endVertex();
    }

    /**
     * 绘制一把待命辉剑的刃身：「十字双面」——在法阵平面内沿垂直于剑身的方向展开一个四边形，
     * 再沿<b>法阵平面法线</b>（即持有者朝向 {@code fwd}）展开另一个。
     * <p>
     * 只画平面内那一面的话，从侧面看剑会退化成一条线；补上沿法线的那一面之后，
     * 从任何角度看都有厚度。手法与 {@code AoeEffectRenderer.lightningSegment} 同源，
     * 区别是展开方向取自法阵的平面基而非世界轴——法阵可以朝任意水平方向，
     * 用世界 X/Z 展开在某些朝向下会退化。
     * </p>
     *
     * @param px1 剑柄端在平面内的 w 分量
     * @param py1 剑柄端在平面内的 u 分量
     * @param px2 剑尖端在平面内的 w 分量
     * @param py2 剑尖端在平面内的 u 分量
     * @param hw  刃半宽
     * @param a1  柄端 alpha
     * @param a2  尖端 alpha
     */
    private static void bladeQuad(BufferBuilder b, Matrix4f m,
                                  float cx, float cy, float cz,
                                  float ux, float uy, float uz,
                                  float wx, float wy, float wz,
                                  float fwdX, float fwdZ,
                                  double px1, double py1, double px2, double py2,
                                  double hw, float[] col, float a1, float a2) {
        if (a1 <= 0.002f && a2 <= 0.002f) {
            return;
        }
        // 面 1：在法阵平面内沿垂直于剑身的方向展开
        planeLine(b, m, cx, cy, cz, ux, uy, uz, wx, wy, wz, px1, py1, px2, py2, hw, col, a1, a2);

        // 面 2：沿法阵平面法线（持有者朝向）展开
        float r = col[0], g = col[1], bl = col[2];
        // 剑身两端的世界坐标（平面内 → 三维）
        float sx1 = cx + wx * (float) px1 + ux * (float) py1;
        float sy1 = cy + wy * (float) px1 + uy * (float) py1;
        float sz1 = cz + wz * (float) px1 + uz * (float) py1;
        float sx2 = cx + wx * (float) px2 + ux * (float) py2;
        float sy2 = cy + wy * (float) px2 + uy * (float) py2;
        float sz2 = cz + wz * (float) px2 + uz * (float) py2;
        // 沿法线（朝向）偏移半宽。朝向是水平单位向量，故 y 分量为 0
        float ox = fwdX * (float) hw;
        float oz = fwdZ * (float) hw;

        b.vertex(m, sx1 - ox, sy1, sz1 - oz).color(r, g, bl, a1).endVertex();
        b.vertex(m, sx1 + ox, sy1, sz1 + oz).color(r, g, bl, a1).endVertex();
        b.vertex(m, sx2 + ox, sy2, sz2 + oz).color(r, g, bl, a2).endVertex();

        b.vertex(m, sx1 - ox, sy1, sz1 - oz).color(r, g, bl, a1).endVertex();
        b.vertex(m, sx2 + ox, sy2, sz2 + oz).color(r, g, bl, a2).endVertex();
        b.vertex(m, sx2 - ox, sy2, sz2 - oz).color(r, g, bl, a2).endVertex();
    }

    /**
     * 面向相机的小菱形光点：中心最亮、四角渐隐。
     * <p>仅 12 顶点，不参与分段缩放；是否绘制由调用方按保留阈值决定。</p>
     * <p>角点内联为标量，零分配（做法与 {@code AoeEffectRenderer} v7 同源）。</p>
     */
    private static void billboardDiamond(BufferBuilder b, Matrix4f m,
                                         float cx, float cy, float cz, float size,
                                         float[] col, float alpha) {
        if (alpha <= 0.004f || size <= 1.0e-4f) {
            return;
        }
        float r = col[0], g = col[1], bl = col[2];
        float rx = VisualBatch.rightX() * size;
        float ry = VisualBatch.rightY() * size;
        float rz = VisualBatch.rightZ() * size;
        float upx = VisualBatch.upX() * size;
        float upy = VisualBatch.upY() * size;
        float upz = VisualBatch.upZ() * size;

        // 四个角点：上、右、下、左（相机平面内）
        float p0x = cx + upx, p0y = cy + upy, p0z = cz + upz;
        float p1x = cx + rx, p1y = cy + ry, p1z = cz + rz;
        float p2x = cx - upx, p2y = cy - upy, p2z = cz - upz;
        float p3x = cx - rx, p3y = cy - ry, p3z = cz - rz;

        diamondTri(b, m, cx, cy, cz, p0x, p0y, p0z, p1x, p1y, p1z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p1x, p1y, p1z, p2x, p2y, p2z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p2x, p2y, p2z, p3x, p3y, p3z, r, g, bl, alpha);
        diamondTri(b, m, cx, cy, cz, p3x, p3y, p3z, p0x, p0y, p0z, r, g, bl, alpha);
    }

    /**
     * 菱形光点的一瓣三角形：中心不透明，两个外角渐隐为 0。
     */
    private static void diamondTri(BufferBuilder b, Matrix4f m,
                                   float cx, float cy, float cz,
                                   float ax, float ay, float az,
                                   float bx, float by, float bz,
                                   float r, float g, float bl, float alpha) {
        b.vertex(m, cx, cy, cz).color(r, g, bl, alpha).endVertex();
        b.vertex(m, ax, ay, az).color(r, g, bl, 0f).endVertex();
        b.vertex(m, bx, by, bz).color(r, g, bl, 0f).endVertex();
    }

    // ==================== 数学辅助 ====================

    /** 缓出（cubic）。 */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }
}
