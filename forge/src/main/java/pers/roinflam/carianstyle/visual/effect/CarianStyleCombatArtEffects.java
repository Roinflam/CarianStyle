package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

/**
 * 服务端战技（COMBAT_SKILL）自绘特效触发入口。
 * <p>
 * 与 {@link CarianStyleBurstParticles} 并列的第二个特效入口类，专供<b>有朝向</b>的战技演出。
 * 本类只负责把「在某位置、朝某方向播放哪种战技演出」广播给附近客户端；真正的视觉由客户端
 * {@code CombatArtEffectRenderer} 用纯顶点几何自绘完成——无贴图、无原版粒子。
 * </p>
 * <p>
 * <b>用法：</b>在附魔的触发点调用对应方法即可，例如：
 * <pre>
 * if (player.level() instanceof ServerLevel serverLevel) {
 *     CarianStyleCombatArtEffects.iaiSlash(serverLevel, player);
 * }
 * </pre>
 * 每个方法都提供「传实体」与「传裸坐标 + yaw」两个重载：前者是常规用法（自动取实体位置与朝向），
 * 后者供需要自定义位置 / 尺寸的场景。
 * </p>
 * <p>
 * <b>性能：</b>每次触发只发一个轻量包（约 25 字节），且经
 * {@link VisualNetwork#sendToNearby} 只广播给附近玩家；特效本身在客户端是纯顶点绘制，
 * 不生成任何实体、不触发任何事件，对服务端 tick 零影响。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class CarianStyleCombatArtEffects {

    /** 特效广播范围（格）：只有该范围内的客户端会收到 */
    private static final double BROADCAST_RANGE = 48.0;

    /**
     * 居合斩默认半径（格）。
     * <p>取 4.0 是因为居合斩本身<b>不是 AOE</b>（只对单一目标造成 ×3.3 伤害），
     * 这个半径纯粹是刀光的视觉尺度——比玩家攻击距离略大，读起来才像「一刀劈开一片空气」，
     * 但又不至于大到让旁观者误以为是范围技。</p>
     */
    private static final float IAI_RADIUS = 4.0f;

    /**
     * 箭步回旋斩默认半径（格）。
     * <p><b>刻意与附魔实际 AOE 半径 3.0 保持一致</b>——回旋斩是真范围技，
     * 刀光扫到哪里就应该打到哪里，视觉与判定对不上会让玩家误判走位。</p>
     */
    private static final float SPIN_RADIUS = 3.0f;

    /**
     * 祈祷一击默认半径（格）。
     * <p>祈祷一击是单体技，此半径仅为落地金环与地面圣徽的视觉尺度。</p>
     */
    private static final float PRAYER_RADIUS = 3.5f;

    private CarianStyleCombatArtEffects() {
    }

    // ============================== 居合斩 ==============================

    /**
     * 居合斩：沿持有者正面扫出一道极快的水平弧形刀光（银白刀锋 + 残影 + 地面切割线）。
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向）
     */
    public static void iaiSlash(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_IAI_SLASH,
                holder.getX(), holder.getY(), holder.getZ(), IAI_RADIUS, holder.getYRot());
    }

    /**
     * 居合斩（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度）
     * @param radius 刀光半径（格）
     */
    public static void iaiSlash(ServerLevel level, double x, double y, double z, float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_IAI_SLASH, x, y, z, radius, yaw);
    }

    // ============================== 箭步回旋斩 ==============================

    /**
     * 箭步回旋斩：绕自身完整扫过 360° 的环形刀光（银白刀锋 + 琥珀扬尘环）。
     * <p>默认半径与附魔实际 AOE 半径一致，视觉即判定。</p>
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向，朝向决定刀光的起始角）
     */
    public static void spinSlash(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_SPIN_SLASH,
                holder.getX(), holder.getY(), holder.getZ(), SPIN_RADIUS, holder.getYRot());
    }

    /**
     * 箭步回旋斩（自定义位置与尺寸）。
     * <p>若附魔的实际 AOE 半径有变动，应同步传入新的 radius，保证视觉与判定一致。</p>
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，决定刀光起始角）
     * @param radius 刀光半径（格）
     */
    public static void spinSlash(ServerLevel level, double x, double y, double z, float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_SPIN_SLASH, x, y, z, radius, yaw);
    }

    // ============================== 祈祷一击 ==============================

    /**
     * 祈祷一击：自天而降的金色圣光柱 + 落地金环 + 地面十字圣徽 + 升腾金光丝。
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向）
     */
    public static void prayerStrike(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_PRAYER_STRIKE,
                holder.getX(), holder.getY(), holder.getZ(), PRAYER_RADIUS, holder.getYRot());
    }

    /**
     * 祈祷一击（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，仅用于让光丝分布逐次略有差异）
     * @param radius 金环 / 圣徽半径（格）
     */
    public static void prayerStrike(ServerLevel level, double x, double y, double z, float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_PRAYER_STRIKE, x, y, z, radius, yaw);
    }

    // ============================== 发包辅助 ==============================

    /**
     * 向触发点附近广播一个战技自绘特效包。
     *
     * @param level  服务端世界
     * @param type   特效类型（见 {@link CombatArtEffectPacket}）
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     * @param yaw    朝向（度）
     */
    private static void send(ServerLevel level, int type,
                             double x, double y, double z, float radius, float yaw) {
        VisualNetwork.sendToNearby(level, x, y, z, BROADCAST_RANGE,
                new CombatArtEffectPacket(type, x, y, z, radius, yaw));
    }
}
