package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 战技自绘特效管理器（纯客户端）。
 * <p>
 * 收到 {@link CombatArtEffectPacket} 后，{@link #spawn} 创建一个带生命周期的
 * {@link CombatArtEffect} 并加入存活列表；客户端每 tick 检查、到期销毁；
 * 各战技渲染器每帧读取列表自绘。所有访问都在客户端主线程
 * （网络 handle 经 enqueueWork、tick / 渲染均主线程），故用普通 {@link ArrayList} 即可，
 * 无并发问题。整体结构与 {@link AoeEffectManager} 同款。
 * </p>
 * <p>
 * <b>与 {@link AoeEffectManager} 的差异：</b>
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
 * <p>
 * 因此它的时长必须<b>足够短</b>（{@value #WATERFOWL_DURATION_MS} ms）：
 * 段间隔只有 2 tick(100ms)，若单段拖到 600ms，四段就会同时挂着四道弧光糊成一片，
 * 反而看不出「连斩」的节奏。
 * </p>
 *
 * <h3>v1.2：新增六个战技演出（4~9）</h3>
 * <p>
 * 全部只需在 {@link #durationFor} 追加一档时长，其余逻辑（进度映射、tick 清理、
 * 上限保护）一律复用，本类没有任何结构性改动。
 * </p>
 *
 * <h3>v1.3：五个新演出的时长上调（实测反馈）</h3>
 * <p>
 * 实测中「狮子斩没看到」「盾牌冲击好像没效果」。除了尺度之外，
 * <b>时长也是原因</b>：v1.2 把它们定在 420~600ms，理由是「触发频率高，做长了会叠成一片」。
 * 这个顾虑本身没错，但方向反了——先得让人<b>看得见一次</b>，
 * 才谈得上担心看见太多次。
 * </p>
 * <p>
 * 本次统一上调约 30%（550~750ms）。这个区间仍明显短于居合的 650ms 之外的两个
 * （回旋 750 / 祈祷 950），连续攻击时也还来得及在下一次触发前收干净。
 * </p>
 * <p>
 * <b>唯一没动的是格挡窗口</b>——它的 500ms 与附魔的 10 tick 反击窗口严格绑定，
 * 不是审美取值，改了视觉就会开始骗人。详见 {@link #PARRY_WINDOW_DURATION_MS}。
 * </p>
 *
 * <h3>关于 {@link #MAX_ACTIVE}</h3>
 * <p>
 * 上限仍保持 {@value #MAX_ACTIVE} 不动。时长上调后单个高攻速玩家同屏挂着的数量
 * 会从十来个升到二十上下，仍在上限内。若将来发现团战中被顶到上限
 * （表现为最早的刀光提前消失），优先考虑的是<b>给高频类型加同位置合并</b>
 * （照抄 {@code AoeEffectManager} 对红闪的三重节流），而不是调大这个上限——
 * 合并能同时省下渲染开销与视觉噪声，调大上限只会两者都增加。
 * </p>
 *
 * @author FlameForge
 * @version 1.3
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtEffectManager {

    /**
     * 存活特效上限。
     * <p>战技特效都很短（&lt;1 秒），正常战斗下同屏不会超过个位数；
     * 此上限仅为极端情况（大量玩家同时触发）下防止无限堆积的兜底。
     * 调整前请先看类注释「关于 MAX_ACTIVE」小节。</p>
     */
    private static final int MAX_ACTIVE = 32;

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
     * 准星收缩到零的那一刻必须恰好是窗口关闭的那一刻——早了玩家会错过还能用的加成，
     * 晚了玩家会以为还有加成而挨一下。<b>如果以后改了附魔里那个 10，
     * 这里必须同步改</b>，否则视觉就在骗人。
     * </p>
     * <p>
     * <b>v1.3 的整体时长上调刻意跳过了这一项</b>，原因即在此。
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
        /** 诞生墙钟时刻（毫秒） */
        public final long birthMs;
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
        ACTIVE.add(new CombatArtEffect(type, x, y, z, radius, toBaseAngle(yaw), now, durationFor(type)));
        // 上限保护：超出则丢弃最早的
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
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
                // 回旋：360° 扫过约 450ms，与附魔里 12tick(600ms) 的玩家旋转动画大致同步，其后扬尘收尾
                return 750L;
            case CombatArtEffectPacket.TYPE_PRAYER_STRIKE:
                // 祈祷一击：光柱降下 + 金环扩散 + 圣徽余辉，稍长以体现「庄重」
                return 950L;
            case CombatArtEffectPacket.TYPE_WATERFOWL_FLURRY:
                // 水鸟乱舞：单道刀光。必须比段间隔（2 tick）的数倍还短，
                // 否则多段会糊成一片、读不出连斩节奏（详见类注释的 v1.1 小节）
                return WATERFOWL_DURATION_MS;
            case CombatArtEffectPacket.TYPE_INDOMITABLE:
                // 不屈壁障：v1.3 450 → 600。免疫是最高 75% 概率的关键正反馈，
                // 残血混战时视野最乱，必须给足被看见的时间
                return 600L;
            case CombatArtEffectPacket.TYPE_LION_CLAW:
                // 狮子斩：v1.3 500 → 650。实测「没看到」，20% 概率配半秒实在太容易漏
                return 650L;
            case CombatArtEffectPacket.TYPE_DOUBLE_SLASH:
                // 二连斩：v1.3 520 → 680。两道交叉刀光，第二道还要错相追上，
                // 时间不够的话第二道刚出来第一道就没了，读不出「二连」
                return 680L;
            case CombatArtEffectPacket.TYPE_LUNGE_UP:
                // 箭步上砍：v1.3 600 → 750。附魔里目标是 5 tick(250ms) 后才被击飞，
                // 拉长后弧顶的火花能压在击飞发生的时刻上
                return 750L;
            case CombatArtEffectPacket.TYPE_PARRY_WINDOW:
                // 格挡窗口：与附魔的 10 tick 反击窗口严格对齐，v1.3 刻意未随其它项上调
                return PARRY_WINDOW_DURATION_MS;
            case CombatArtEffectPacket.TYPE_SHIELD_BASH:
                // 盾牌冲击：v1.3 420 → 550。实测「好像没效果」，
                // 举盾挨打时视角常在晃（被击退 / 被打断），420ms 太容易整个错过
                return 550L;
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
    public static float progressFor(CombatArtEffect fx, long now) {
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
