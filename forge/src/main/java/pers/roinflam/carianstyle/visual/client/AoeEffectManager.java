package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import java.util.ArrayList;
import java.util.List;

/**
 * 定点 AOE 自绘特效管理器（纯客户端）。
 * <p>
 * 收到 {@link AoeEffectPacket} 后，{@link #spawn} 创建一个带生命周期的
 * {@link AoeEffect} 并加入存活列表；客户端每 tick 推进、到期销毁；{@link AoeEffectRenderer} 每帧读取
 * 列表自绘。所有访问都在客户端主线程（网络 handle 经 enqueueWork、tick/渲染均主线程），故用普通
 * {@link ArrayList} 即可，无并发问题。
 * </p>
 * <p>
 * <b>说明：</b>特效时长是<b>纯客户端的视觉播放时长</b>，与服务端附魔的实际机制时长（无敌、拉取、伤害）
 * 完全解耦——延长视觉时长不影响任何游戏机制。猩红罗妮亚 / 癫火蔓延为多段演出，时长按
 * 「触发即满状态蓄能（覆盖约 1.5 秒拉取无敌前摇）→ 大爆发 → 长余波」编排，故较长。
 * </p>
 *
 * @author RoinFlam
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AoeEffectManager {

    /** 存活特效上限（极端情况下防止无限堆积） */
    private static final int MAX_ACTIVE = 64;

    /**
     * 死亡演出（猩红立体花 SCARLET_BLOOM / 癫火扩散 FRENZIED_FLAME）整体尺寸放大倍数。
     * <p>基础半径 5.0f × {@code 1.5} = 7.5f（主体直径约 15 格；注意「爆发冲击环」为半径×1.9，
     * 故环直径约 28 格——这是观感上最占视野的部分）。环类基元分段数由
     * {@code AoeEffectRenderer.segmentsFor} 封顶 72，改此常量不会导致顶点爆炸，线宽随半径同步缩放。
     * <b>要继续调小只改此值即可</b>：1.2≈主体 12 格、1.0≈主体 10 格（=不额外放大、与作用范围相当）。</p>
     */
    private static final float DEAD_EFFECT_SCALE = 1.5f;

    /** 当前存活特效列表（仅客户端主线程访问） */
    private static final List<AoeEffect> ACTIVE = new ArrayList<>();

    /**
     * 红色闪电「同位置合并」判定半径（格）的平方。
     * <p>新落雷点与某存活红闪的水平距离在此范围内，即视为「同一道持续的雷」，只续命不新建——
     * 用于消除古龙雷击等高频重复降雷在同一处叠加多道闪电造成的「鬼畜」跳变。要让相邻目标更容易
     * 各自独立成一道就调小，要让同一目标移动时更不易分裂成多道就调大。</p>
     */
    private static final double RED_LIGHTNING_MERGE_DIST_SQR = 2.5 * 2.5;

    private AoeEffectManager() {
    }

    /**
     * 一个正在播放的定点特效实例。
     * <p>动画进度由墙钟 age 驱动（与世界 tick 解耦，避免取模回绕等问题）：
     * progress = (now - birthMs) / durationMs。</p>
     */
    public static final class AoeEffect {
        /** 特效类型（见 {@link AoeEffectPacket}） */
        public final int type;
        /** 绑定实体 id；{@link AoeEffectPacket#NO_ENTITY}(-1) 表示定点不跟随 */
        public final int entityId;
        /**
         * 世界坐标。
         * <p>定点特效：恒为发包坐标。跟随特效：每帧由渲染器更新为实体的实时插值位置，作为
         * 「最后已知坐标」——实体死亡 / 移除后渲染器即用此坐标继续播放剩余演出（残留在原地），
         * 故去掉 final。</p>
         */
        public double x;
        public double y;
        public double z;
        /** 半径（格） */
        public final float radius;
        /**
         * 固定外形种子（当前仅红色闪电使用）。
         * <p>由管理器在创建特效时生成、整段生命周期内不变——即便红闪因「同位置合并」被反复续命
         * （重置 {@link #birthMs}），其外形也由本字段恒定决定，不会逐次跳变。</p>
         */
        public final long seed;
        /**
         * 诞生墙钟时刻（毫秒）。
         * <p>红色闪电存在「同位置合并续命」：重复落雷到同一处时不新建特效，而是把已存在那道的本字段
         * 重置为当前时刻以延长播放（表现为一道持续劈着的雷），故去掉 final。</p>
         */
        public long birthMs;
        /** 总时长（毫秒） */
        public final long durationMs;

        AoeEffect(int type, int entityId, double x, double y, double z,
                  float radius, long seed, long birthMs, long durationMs) {
            this.type = type;
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.seed = seed;
            this.birthMs = birthMs;
            this.durationMs = durationMs;
        }
    }

    /**
     * 创建一个定点特效（由网络包在客户端主线程调用，历史签名）。
     * <p>等价于 {@code spawn(type, x, y, z, radius, AoeEffectPacket.NO_ENTITY)}，即不跟随。</p>
     *
     * @param type   特效类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     */
    public static void spawn(int type, double x, double y, double z, float radius) {
        spawn(type, x, y, z, radius, AoeEffectPacket.NO_ENTITY);
    }

    /**
     * 创建一个特效（可绑定实体跟随，由网络包在客户端主线程调用）。
     * <p><b>红色闪电特例：</b>古龙雷击等会每数 tick 对同一目标重复降雷，若每次都新建特效，同一处会
     * 同时叠着多道形态各异的闪电并不断新生，视觉上呈高频「鬼畜」跳变。故红闪在新建前先尝试
     * {@link #refreshNearbyRedLightning 同位置合并}：附近已有存活红闪时只续命、不新建。</p>
     *
     * @param type     特效类型
     * @param x        世界坐标 X（跟随特效用作初始 / 实体消失后的回退坐标）
     * @param y        世界坐标 Y
     * @param z        世界坐标 Z
     * @param radius   半径（格）
     * @param entityId 绑定实体 id；{@link AoeEffectPacket#NO_ENTITY}(-1) 为定点，{@code >=0} 为跟随
     */
    public static void spawn(int type, double x, double y, double z, float radius, int entityId) {
        long now = System.currentTimeMillis();
        // 红色闪电同位置合并：避免高频重复降雷在同一处叠加多道闪电导致「鬼畜」
        if (type == AoeEffectPacket.TYPE_RED_LIGHTNING && refreshNearbyRedLightning(x, y, z, now)) {
            return;
        }
        // v6.1：死亡演出（猩红立体花 / 癫火扩散）半径按 scaleFor 放大后再存储；
        // CULL 裁剪与渲染均使用放大后半径，全链一致，避免大花被误裁
        float scaledRadius = radius * scaleFor(type);
        ACTIVE.add(new AoeEffect(type, entityId, x, y, z, scaledRadius, makeSeed(now, x, z), now, durationFor(type)));
        // 上限保护：超出则丢弃最早的
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    /**
     * 红色闪电「同位置合并」：若落点附近已存在存活红闪，则把它更新到新落点并重置生命周期
     * （续命，使其表现为一道持续劈着、缓慢明灭的雷），返回 {@code true} 表示本次应合并、不新建；
     * 附近无存活红闪则返回 {@code false}（照常新建）。
     * <p>其外形种子 {@link AoeEffect#seed} 在续命时<b>保持不变</b>，故反复续命也不会跳变外形。</p>
     *
     * @param x   新落点世界坐标 X
     * @param y   新落点世界坐标 Y
     * @param z   新落点世界坐标 Z
     * @param now 当前墙钟（毫秒）
     * @return 是否已合并到既有红闪（true 则调用方不应再新建）
     */
    private static boolean refreshNearbyRedLightning(double x, double y, double z, long now) {
        for (int i = 0; i < ACTIVE.size(); i++) {
            AoeEffect fx = ACTIVE.get(i);
            if (fx.type != AoeEffectPacket.TYPE_RED_LIGHTNING) {
                continue;
            }
            double dx = fx.x - x;
            double dz = fx.z - z;
            if (dx * dx + dz * dz <= RED_LIGHTNING_MERGE_DIST_SQR) {
                fx.x = x;
                fx.y = y;
                fx.z = z;
                fx.birthMs = now;
                return true;
            }
        }
        return false;
    }

    /**
     * 生成一个非 0 的固定外形种子（散列自创建时刻与落点坐标）。
     * <p>存入 {@link AoeEffect#seed} 后整段生命周期不变，使每道特效外形稳定、不同特效外形各异。</p>
     *
     * @param now 创建时墙钟（毫秒）
     * @param x   落点世界坐标 X
     * @param z   落点世界坐标 Z
     * @return 非 0 种子
     */
    private static long makeSeed(long now, double x, double z) {
        long bits = now * 1099511628211L
                ^ Double.doubleToLongBits(x) * 31
                ^ Double.doubleToLongBits(z) * 17;
        bits ^= (bits >>> 33);
        return bits == 0 ? 0x9E3779B97F4A7C15L : bits;
    }

    /**
     * 各类型特效尺寸放大系数。
     * <p>猩红立体花 / 癫火扩散为死亡高潮演出，整体放大以匹配其分量；其余特效保持原尺寸。</p>
     *
     * @param type 特效类型
     * @return 半径放大倍数（1.0 为不放大）
     */
    private static float scaleFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return DEAD_EFFECT_SCALE;
            default:
                return 1.0f;
        }
    }

    /**
     * 各类型特效时长（毫秒）。
     *
     * @param type 特效类型
     * @return 时长
     */
    private static long durationFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_CAUSALITY:
                return 1100L;
            case AoeEffectPacket.TYPE_FROST_QUAKE:
                return 1000L;
            case AoeEffectPacket.TYPE_REPULSION:
                return 520L;
            case AoeEffectPacket.TYPE_RED_LIGHTNING:
                // 龙雷红色闪电：还原原作——巨大亮眼、持续强烈明灭、消散较慢（线性映射，无分段）
                return 1400L;
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
                // 立体花总时长。配合 progressFor 的分段映射：前 1500ms（chargeUpMsFor）把 progress
                // 推进到盛放主点 0.42（恒对齐附魔 30tick 第二阶段爆发），其后 3900ms 把 progress
                // 推进到 1.0（凋谢余波段）。加长总时长只拉长凋谢、不影响盛放时机。
                // 5400 = 1500ms 绽放蓄能（对齐爆发）+ 3900ms 盛放 / 凋谢长余波
                return 5400L;
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                // 癫火总时长。同样经 progressFor 分段：前 1500ms 推进到爆发主点 0.42（对齐 30tick），
                // 其后 3900ms 推进到 1.0（焦黑余烬段）。
                // 5400 = 1500ms 狂乱蓄能（对齐爆发）+ 3900ms 爆发 / 余烬长尾
                return 5400L;
            default:
                return 700L;
        }
    }

    /**
     * 死亡演出（猩红立体花 / 癫火扩散）的「蓄能段绝对时长」（毫秒）。
     * <p>蓄能段（花苞缓慢绽放 → 盛放前一刻）固定占用这段绝对时间，使盛放主点恒定对齐附魔
     * 30tick(1500ms) 的第二阶段爆发——无论总时长 {@link #durationFor} 调多长，盛放时机都不漂移；
     * 加长只拉长盛放之后的爆发 / 凋谢余波。其余类型返回 0（表示按总时长线性播放，无分段）。</p>
     *
     * @param type 特效类型
     * @return 蓄能段绝对时长（毫秒）；0 表示线性播放
     */
    private static long chargeUpMsFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return 1500L; // 对齐附魔 30tick 第二阶段爆发
            default:
                return 0L;
        }
    }

    /**
     * 死亡演出盛放主点的归一化进度值（蓄能段结束时 progress 恰好到达此值）。
     * <p>与渲染器 {@code drawScarletBloom} / {@code drawFrenziedFlame} 时间轴里的盛放主点
     * （p≈0.42）保持一致：蓄能段把 progress 从 0 推进到 0.42，剩余时长把 progress 从 0.42
     * 推进到 1.0。这样渲染器时间轴的所有归一化魔数（绽放 / 盛放 / 爆发 / 凋谢分界）<b>无需改动</b>，
     * 仅靠分段 progress 即可在「盛放对齐 30tick」前提下任意拉长总时长。</p>
     *
     * @param type 特效类型
     * @return 盛放主点归一化值；分段无效时返回 0
     */
    private static float bloomPointFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return 0.42f;
            default:
                return 0f;
        }
    }

    /**
     * 计算某特效当前的归一化播放进度 progress∈[0,1]（供渲染器调用）。
     * <p>死亡演出（{@link #chargeUpMsFor} &gt; 0）采用<b>分段</b>映射：前 {@code chargeUp} 毫秒把
     * progress 从 0 线性推进到盛放主点 {@link #bloomPointFor}（恒对齐 30tick 爆发），其后用剩余
     * 时长把 progress 从盛放主点线性推进到 1.0（凋谢余波段，随总时长加长而拉长）。其余类型按总
     * 时长线性映射。这样「加长特效」只延长盛放之后的余波，盛放时机与附魔机制始终同步。</p>
     *
     * @param fx  特效实例
     * @param now 当前墙钟（毫秒）
     * @return 归一化进度，夹取到 [0,1]
     */
    public static float progressFor(AoeEffect fx, long now) {
        long elapsed = now - fx.birthMs;
        if (elapsed <= 0L) {
            return 0f;
        }
        long chargeUp = chargeUpMsFor(fx.type);
        if (chargeUp <= 0L) {
            // 线性映射（非死亡类型）
            return clamp01(elapsed / (float) fx.durationMs);
        }
        float bloom = bloomPointFor(fx.type);
        if (elapsed < chargeUp) {
            // 蓄能段：0 → 盛放主点
            return bloom * (elapsed / (float) chargeUp);
        }
        // 凋谢段：盛放主点 → 1.0
        long rest = fx.durationMs - chargeUp;
        if (rest <= 0L) {
            return 1f;
        }
        return bloom + (1f - bloom) * clamp01((elapsed - chargeUp) / (float) rest);
    }

    /**
     * 夹取到 0~1。
     *
     * @param v 输入
     * @return 夹取结果
     */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /**
     * 客户端 tick 末尾：推进并移除到期特效；离开世界时清空。
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
    public static List<AoeEffect> getActive() {
        return ACTIVE;
    }

    /**
     * 清空（离开世界等场景）。
     */
    public static void clear() {
        ACTIVE.clear();
    }
}
