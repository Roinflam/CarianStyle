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
 * {@link CombatArtEffectRenderer} 每帧读取列表自绘。所有访问都在客户端主线程
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
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class CombatArtEffectManager {

    /**
     * 存活特效上限。
     * <p>战技特效都很短（&lt;1 秒），正常战斗下同屏不会超过个位数；
     * 此上限仅为极端情况（大量玩家同时触发）下防止无限堆积的兜底。</p>
     */
    private static final int MAX_ACTIVE = 32;

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
