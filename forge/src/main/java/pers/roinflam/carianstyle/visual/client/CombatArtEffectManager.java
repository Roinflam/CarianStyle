package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 战技自绘特效管理器（纯客户端）。
 * <p>
 * 收到 {@link CombatArtEffectPacket} 后，{@link #spawn} 创建一个带生命周期的
 * {@link CombatArtEffect} 并加入存活列表；客户端每 tick 检查、到期销毁；
 * 各战技渲染器每帧读取列表自绘。所有访问都在客户端主线程
 * （网络 handle 经 enqueueWork、tick / 渲染均主线程），故用普通 {@link ArrayList} 即可，
 * 无并发问题。整体结构与 {@code AoeEffectManager} 同款。
 * </p>
 * <p>
 * <b>与 {@code AoeEffectManager} 的差异：</b>
 * <ul>
 *     <li>多一个 {@code yaw} 字段（持有者朝向），刀光据此确定扫过的方位；</li>
 *     <li><b>不支持跟随实体</b>——刀光是「挥出去的那一瞬间留在空间里的痕迹」，
 *         跟着人移动反而不合理，故坐标一经创建即固定；</li>
 *     <li><b>进度为纯线性映射</b>——战技都是 1 秒内的短促演出，不存在需要对齐
 *         延迟触发第二阶段的分段需求（那是死亡类附魔才有的场景）。</li>
 * </ul>
 * </p>
 *
 * <h3>v1.1：新增水鸟乱舞</h3>
 * <p>
 * 这是本管理器第一个「一次攻击发多个包」的类型——
 * {@code EnchantmentWaterfowlFlurry} 每一段攻击各发一包（共 level+1 段、每 2 tick 一段），
 * 多道刀光靠各自独立的 {@code birthMs} 自然错相叠成连斩。
 * </p>
 *
 * <h3>v1.2 / v1.3：新增六个 + 五个战技演出</h3>
 * <p>
 * 时长全部收敛在 550~950ms 区间，靠 {@link #durationFor} 一处分发。
 * </p>
 *
 * <h3>v1.4（本次）：高频类型的同位置合并，与上限上调</h3>
 *
 * <h4>为什么必须加合并</h4>
 * <p>
 * v1.3 之前的九个类型，触发条件全是「概率」或「特定动作」——
 * 居合 1% 起、回旋要冲刺攻击、格挡要架住、狮子斩 20%……
 * 单个玩家同屏挂着的数量是个位数，{@code MAX_ACTIVE}(32) 绰绰有余。
 * </p>
 * <p>
 * <b>v1.3 新增的这批不一样。</b>血刃、挥石魔法、黄金律法
 * 都是<b>只要满足条件、每一次攻击都触发</b>的：
 * </p>
 * <pre>
 * 一个高攻速、装了这几件的玩家
 *   → 每次挥砍同时产生 血刃 + 挥石 + 律法 三个特效
 *   → 攻速 2.0 即每秒 6 个
 *   → 每个活 0.6~0.7 秒
 *   → 单人稳态挂着约 4 个
 * 十人团战 → 约 40 个，接近 32 的上限
 * </pre>
 * <p>
 * 上限被打穿的表现是<b>最早的特效提前消失</b>——玩家会看到刀光闪一半就没了，
 * 比不画还难看。
 * </p>
 *
 * <h4>做法：同位置合并（照抄红闪那套）</h4>
 * <p>
 * {@code AoeEffectManager} 对红色闪电用的是「合并半径 + 专用上限 + 新建限速」三重节流。
 * 本类只需要其中最有效的第一重：<b>同一类型、{@value #MERGE_DIST} 格内已有实例时，
 * 不新建、只把那个实例的 {@code birthMs} 重置</b>（表现为「同一道特效持续在闪」）。
 * </p>
 * <p>
 * 这对本场景特别贴切：这几个高频特效都锚在<b>同一个实体</b>身上（自己或同一个目标），
 * 位移在 0.7 秒内不足半格，合并半径 1.2 格足以覆盖，而两个不同的人 / 不同的目标
 * 必定分得开。
 * </p>
 * <p>
 * <b>只对 {@link #shouldMerge} 列出的类型生效。</b>居合、回旋、水鸟这些刻意要
 * 「多道错相叠加」的类型<b>绝不能合并</b>——水鸟乱舞的连斩感完全依赖多个实例同时存在，
 * 合并了就只剩一道刀光。这是本次改动唯一需要小心的地方。
 * </p>
 *
 * <h4>上限同步提到 {@value #MAX_ACTIVE}</h4>
 * <p>
 * 合并之后单人稳态降回 3 个（每个附魔一道，不再随攻速堆叠），
 * 十人团战约 30 个。48 是留了余量的估算值。
 * v1.4 移除四个演出后压力进一步下降，但上限保持不变——留着余量没有成本。
 * </p>
 * <p>
 * <b>刻意不再往上调：</b>再高就该考虑给某个类型加专用上限了（照抄红闪的第二重），
 * 而不是无限放宽总量——总量放宽只会让远处一堆看不清的刀光继续吃掉填充率。
 * </p>
 *
 * @author FlameForge
 *
 * <h3>v1.4（同批）：移除四个演出的时长与合并登记</h3>
 * <p>
 * 复仇誓言、战士、碎星、献斗剑四个类型已删除，本类中对应的
 * {@link #durationFor} 分支与 {@link #shouldMerge} 登记一并移除。
 * </p>
 *
 * @version 1.4
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtEffectManager {

    /**
     * 存活特效上限。
     * <p>
     * v1.4：由 32 提到 {@value}，配合同位置合并一起应对 v1.3 新增的高频类型
     * （详见类注释「上限同步提到」小节）。
     * </p>
     */
    private static final int MAX_ACTIVE = 48;

    /**
     * 同位置合并的判定半径（格）。
     * <p>
     * 取 {@value} 的依据：需要合并的那几个特效都锚在同一个实体身上，
     * 而实体在单个特效的生命周期（0.6~0.8 秒）内的位移通常不足半格；
     * 而两个不同实体的间距至少是各自碰撞箱之和（&gt;0.6 格），
     * 再加上战斗中的走位，1.2 格能可靠地把「同一个人反复触发」与
     * 「两个人各自触发」分开。
     * </p>
     * <p>调大会让贴身站位的两名玩家共用一道特效，调小会让合并失效。</p>
     */
    private static final double MERGE_DIST = 1.2;

    /** {@link #MERGE_DIST} 的平方（避免开方） */
    private static final double MERGE_DIST_SQR = MERGE_DIST * MERGE_DIST;

    /**
     * 水鸟乱舞单道刀光的时长（毫秒）。
     * <p>取值理由见类注释的「v1.1」小节——必须显著短于附魔的段间隔（2 tick = 100ms）
     * 的数倍，才能让连斩读作「一道接一道」而不是「一团糊」。</p>
     */
    private static final long WATERFOWL_DURATION_MS = 380L;

    /**
     * 格挡窗口的时长（毫秒）。
     * <p>
     * <b>⚠ 这个值不是「看着合适」挑出来的，而是被机制锁死的。</b>
     * {@code EnchantmentParry.onLivingAttack} 里写的是
     * {@code EnchantmentDataManager.setData(PARRY_LEVEL_KEY, uuid, level, 10)}，
     * 即反击加成的有效期为 <b>10 tick = 500ms</b>。
     * </p>
     * <p>
     * 准星收缩到零的那一刻必须恰好是窗口关闭的那一刻。
     * <b>如果以后改了附魔里那个 10，这里必须同步改</b>，否则视觉就在骗人。
     * </p>
     */
    private static final long PARRY_WINDOW_DURATION_MS = 500L;

    /** 当前存活特效列表（仅客户端主线程访问） */
    private static final List<CombatArtEffect> ACTIVE = new ArrayList<>();

    private CombatArtEffectManager() {
    }

    /**
     * 一个正在播放的战技特效实例。
     * <p>动画进度由墙钟 age 驱动（与世界 tick 解耦，避免 TPS 波动导致刀光忽快忽慢）：
     * progress = (now - birthMs) / durationMs。</p>
     */
    public static final class CombatArtEffect {
        /** 特效类型（见 {@link CombatArtEffectPacket}） */
        public final int type;
        /** 世界坐标 X（创建后固定，不跟随实体） */
        public final double x;
        /** 世界坐标 Y（持有者脚底） */
        public final double y;
        /** 世界坐标 Z */
        public final double z;
        /** 半径（格） */
        public final float radius;
        /** 持有者水平朝向（弧度，已由度数转换并折算为本项目极坐标口径，详见 {@link #spawn}） */
        public final float baseAngle;
        /**
         * 诞生墙钟时刻（毫秒）。
         * <p>v1.4：高频类型存在「同位置合并续命」，合并时会把本字段重置为当前时刻，
         * 故去掉 final（与 {@code AoeEffectManager.AoeEffect#birthMs} 同理）。</p>
         */
        public long birthMs;
        /** 总时长（毫秒） */
        public final long durationMs;

        CombatArtEffect(int type, double x, double y, double z,
                        float radius, float baseAngle, long birthMs, long durationMs) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.baseAngle = baseAngle;
            this.birthMs = birthMs;
            this.durationMs = durationMs;
        }
    }

    /**
     * 创建一个战技特效（由网络包在客户端主线程调用）。
     * <p>
     * v1.4：高频类型（见 {@link #shouldMerge}）在新建前先尝试
     * {@link #tryMerge 同位置合并}，命中则只续命、不新建。
     * </p>
     *
     * @param type   特效类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y（脚底）
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     * @param yaw    持有者水平朝向（度，Minecraft {@code getYRot()} 口径）
     */
    public static void spawn(int type, double x, double y, double z, float radius, float yaw) {
        long now = System.currentTimeMillis();
        // ⭐ v1.4：高频类型的同位置合并。命中即返回，不再新建
        if (shouldMerge(type) && tryMerge(type, x, y, z, now)) {
            return;
        }
        ACTIVE.add(new CombatArtEffect(type, x, y, z, radius, toBaseAngle(yaw), now, durationFor(type)));
        // 上限保护：超出则丢弃最早的
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    /**
     * 该类型是否参与同位置合并。
     * <p>
     * <b>⚠ 只列出「每次攻击都可能触发」的类型。</b>刻意要多道错相叠加的类型
     * （尤其是水鸟乱舞——它的连斩感完全依赖多个实例同时存在）绝不能出现在这里，
     * 否则会被合并成一道，整个演出就废了。
     * </p>
     * <p>
     * 判断标准很简单：<b>这个附魔在一次普通攻击里会不会稳定触发？</b>
     * 会 → 加入；靠概率 / 靠特定动作（冲刺、举盾、击杀）→ 不加入。
     * </p>
     *
     * @param type 特效类型
     * @return 参与合并返回 true
     */
    private static boolean shouldMerge(int type) {
        switch (type) {
            case CombatArtEffectPacket.TYPE_BLOOD_BLADE:
            case CombatArtEffectPacket.TYPE_GOLDEN_LAW:
            case CombatArtEffectPacket.TYPE_WAVE_STONE:
                return true;
            default:
                return false;
        }
    }

    /**
     * 尝试把本次触发合并到附近同类型的已有实例上。
     * <p>
     * 命中时把那个实例的 {@code birthMs} 重置为当前时刻——表现为「同一道特效重新播了一遍」，
     * 而不是叠出第二道。<b>刻意不改坐标与朝向</b>：同一个实体反复触发时二者本就几乎不变，
     * 改了反而会让特效在两次触发之间轻微跳动。
     * </p>
     *
     * @param type 特效类型
     * @param x    新触发点 X
     * @param y    新触发点 Y
     * @param z    新触发点 Z
     * @param now  当前墙钟（毫秒）
     * @return 已合并返回 true（调用方不应再新建）
     */
    private static boolean tryMerge(int type, double x, double y, double z, long now) {
        for (int i = 0; i < ACTIVE.size(); i++) {
            CombatArtEffect fx = ACTIVE.get(i);
            if (fx.type != type) {
                continue;
            }
            double dx = fx.x - x;
            double dy = fx.y - y;
            double dz = fx.z - z;
            if (dx * dx + dy * dy + dz * dz <= MERGE_DIST_SQR) {
                fx.birthMs = now;
                return true;
            }
        }
        return false;
    }

    /**
     * 把 Minecraft 的 yaw（度）转换为本项目几何基元使用的极坐标角（弧度）。
     * <p>
     * <b>换算依据：</b>Minecraft 中 yaw=0 面向 +Z（南），yaw=90 面向 -X（西），
     * 故朝向单位向量为 {@code (dx, dz) = (-sin(yaw), cos(yaw))}。
     * 而本项目所有环 / 弧基元均按 {@code 点 = (cx + r·cos(a), cz + r·sin(a))} 取点，
     * 即角 a 对应 {@code atan2(dz, dx)}。代入朝向向量：
     * {@code a = atan2(cos(yaw), -sin(yaw)) = yaw + 90°}。
     * </p>
     * <p>校验：yaw=0 → a=90°，代入得点在 +Z 方向 ✓；yaw=90° → a=180°，点在 -X 方向 ✓。</p>
     *
     * @param yawDegrees Minecraft 朝向（度）
     * @return 极坐标基准角（弧度），指向持有者正前方
     */
    private static float toBaseAngle(float yawDegrees) {
        return (float) (Math.toRadians(yawDegrees) + Math.PI / 2.0);
    }

    /**
     * 各类型特效时长（毫秒）。
     * <p>战技演出都刻意做得短促干脆——刀光拖太久会显得黏滞、失去「快」的观感。</p>
     *
     * @param type 特效类型
     * @return 时长（毫秒）
     */
    private static long durationFor(int type) {
        switch (type) {
            case CombatArtEffectPacket.TYPE_IAI_SLASH:
                // 居合：拔刀一瞬（弧光在前 25% 扫完）+ 残影消散
                return 650L;
            case CombatArtEffectPacket.TYPE_SPIN_SLASH:
                // 回旋：360° 扫过约 450ms，与附魔里 12tick(600ms) 的玩家旋转动画大致同步
                return 750L;
            case CombatArtEffectPacket.TYPE_PRAYER_STRIKE:
                // 祈祷一击：光柱降下 + 金环扩散 + 圣徽余辉，稍长以体现「庄重」
                return 950L;
            case CombatArtEffectPacket.TYPE_WATERFOWL_FLURRY:
                // 水鸟乱舞：单道刀光。必须比段间隔（2 tick）的数倍还短，
                // 否则多段会糊成一片、读不出连斩节奏（详见类注释的 v1.1 小节）
                return WATERFOWL_DURATION_MS;
            case CombatArtEffectPacket.TYPE_INDOMITABLE:
                // 不屈壁障：免疫是最高 75% 概率的关键正反馈，残血混战时视野最乱，必须给足时间
                return 600L;
            case CombatArtEffectPacket.TYPE_LION_CLAW:
                return 650L;
            case CombatArtEffectPacket.TYPE_DOUBLE_SLASH:
                // 二连斩：第二道要错相追上，时间不够会读不出「二连」
                return 680L;
            case CombatArtEffectPacket.TYPE_LUNGE_UP:
                // 箭步上砍：附魔里目标是 5 tick(250ms) 后才被击飞，弧顶火花需压在那一刻
                return 750L;
            case CombatArtEffectPacket.TYPE_PARRY_WINDOW:
                // 格挡窗口：与附魔的 10 tick 反击窗口严格对齐，不可随意改
                return PARRY_WINDOW_DURATION_MS;
            case CombatArtEffectPacket.TYPE_SHIELD_BASH:
                return 550L;

            // ===== 数值型附魔的打击反馈（10 / 12 / 14 / 17 / 18）=====
            case CombatArtEffectPacket.TYPE_BLOOD_BLADE:
                // 血刃：地面血环扩散 + 放射溅射 + 低矮血泉 + 血滴。
                // 这是自伤反馈，必须让玩家来得及意识到「我刚扣了 15% 血」
                return 700L;
            case CombatArtEffectPacket.TYPE_WAVE_STONE:
                // 挥石魔法：钝重横扫 + 碎石飞散。做长了会失去「抡一下」的干脆
                return 580L;
            case CombatArtEffectPacket.TYPE_GOLDEN_LAW:
                // 黄金律法：地面碑文铺开 + 刻纹亮起 + 金环外扩。庄重感需要一点时长
                return 650L;
            case CombatArtEffectPacket.TYPE_SKY_SHOT:
                // 对空射击：贯下的箭光 + 爆环 + 垂下的光柱，都需要走完全程的时间
                return 800L;
            case CombatArtEffectPacket.TYPE_HARD_ARROW:
                // 硬箭：十字冲击 + 后退环。弓箭连射频率高，做长了会叠成一片
                return 600L;
            default:
                return 700L;
        }
    }

    /**
     * 计算某特效当前的归一化播放进度 progress∈[0,1]（供渲染器调用）。
     * <p>战技特效统一线性映射，无分段。</p>
     *
     * @param fx  特效实例
     * @param now 当前墙钟（毫秒）
     * @return 归一化进度，夹取到 [0,1]
     */
    public static float progressFor(@Nonnull CombatArtEffect fx, long now) {
        long elapsed = now - fx.birthMs;
        if (elapsed <= 0L) {
            return 0f;
        }
        float p = elapsed / (float) fx.durationMs;
        if (p < 0f) {
            return 0f;
        }
        return Math.min(p, 1f);
    }

    /**
     * 客户端 tick 末尾：移除到期特效；离开世界时清空。
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (Minecraft.getInstance().level == null) {
            ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(fx -> now - fx.birthMs >= fx.durationMs);
    }

    /**
     * @return 当前存活特效列表（渲染线程只读）
     */
    public static List<CombatArtEffect> getActive() {
        return ACTIVE;
    }

    /**
     * 清空（离开世界等场景）。
     */
    public static void clear() {
        ACTIVE.clear();
    }
}
