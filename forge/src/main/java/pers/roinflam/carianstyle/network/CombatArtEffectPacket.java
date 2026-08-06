package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.carianstyle.visual.client.CombatArtEffectManager;

import java.util.function.Supplier;

/**
 * 战技（COMBAT_SKILL）自绘特效触发包（S2C，服务端 -> 客户端）。
 * <p>
 * 与 {@link AoeEffectPacket} 并列的第二条瞬时特效链路，专供<b>有朝向</b>的战技演出。
 * </p>
 * <p>
 * <b>为什么不复用 {@link AoeEffectPacket}：</b>本包多携带一个 {@link #yaw} 字段。
 * 居合斩、回旋斩这类刀光必须知道「持有者面朝哪个方向」才能把弧光扫在正确的方位上，
 * 而 {@code AoeEffectPacket} 只有坐标、没有朝向——它服务的是「以某点为圆心的对称法阵」
 * （六芒星、霜环、立体花），这些图形本就与朝向无关。给它加字段意味着要改动其渲染器里
 * 全部既有演出的分发路径，风险与收益不成比例，故另起一套，两者互不影响。
 * </p>
 * <p>
 * <b>与 {@code AoeEffectPacket} 的其它差异：</b>战技特效都是<b>短促瞬时</b>的（0.6~1 秒），
 * 不支持跟随实体（刀光挥出去就固定在挥出时的位置，跟着人走反而不合理），
 * 也没有分段进度映射（无需对齐延迟触发的第二阶段）。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public class CombatArtEffectPacket {

    // ===== 效果类型（与客户端 CombatArtEffectManager / CombatArtEffectRenderer 的分发严格一致）=====

    /**
     * 居合斩：沿持有者正面扫出的一道极快水平弧形刀光（银白刀锋 + 残影 + 地面切割线）。
     * <p>对应 {@code EnchantmentUnsheathe} 概率触发的居合斩。</p>
     */
    public static final int TYPE_IAI_SLASH = 0;

    /**
     * 回旋斩：绕自身完整扫过 360° 的环形刀光（银白刀锋 + 琥珀扬尘环）。
     * <p>对应 {@code EnchantmentStampSweep} 冲刺攻击时的箭步回旋斩。</p>
     */
    public static final int TYPE_SPIN_SLASH = 1;

    /**
     * 祈祷一击：自天而降的金色圣光柱 + 落地金环 + 地面十字圣徽。
     * <p>对应 {@code EnchantmentPrayerfulStrike} 蓄力完成后的那一击。</p>
     */
    public static final int TYPE_PRAYER_STRIKE = 2;

    /** 效果类型（见上方常量） */
    private final int type;
    /** 世界坐标 X（持有者所在位置） */
    private final double x;
    /** 世界坐标 Y（持有者脚底） */
    private final double y;
    /** 世界坐标 Z */
    private final double z;
    /** 半径（格）：决定刀光弧带 / 光环的整体尺寸 */
    private final float radius;
    /**
     * 持有者的水平朝向（度，Minecraft 的 {@code getYRot()} 口径）。
     * <p>决定居合斩弧光扫过的方位、回旋斩的起始角。对朝向无关的演出（如祈祷一击的光柱）
     * 该值仅用于让不同次触发的细节（火花分布等）略有差异，不影响主体形态。</p>
     */
    private final float yaw;

    /**
     * 构造。
     *
     * @param type   效果类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y（脚底）
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     * @param yaw    持有者水平朝向（度）
     */
    public CombatArtEffectPacket(int type, double x, double y, double z, float radius, float yaw) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.yaw = yaw;
    }

    public int getType() {
        return type;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getRadius() {
        return radius;
    }

    public float getYaw() {
        return yaw;
    }

    /**
     * 编码。
     *
     * @param packet 待编码包
     * @param buf    目标缓冲
     */
    public static void encode(CombatArtEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.type);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeFloat(packet.yaw);
    }

    /**
     * 解码。
     *
     * @param buf 源缓冲
     * @return 解码出的包
     */
    public static CombatArtEffectPacket decode(FriendlyByteBuf buf) {
        int type = buf.readByte();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float radius = buf.readFloat();
        float yaw = buf.readFloat();
        return new CombatArtEffectPacket(type, x, y, z, radius, yaw);
    }

    /**
     * 处理（仅客户端执行：本包为 S2C）。
     * <p>
     * 直接在主线程任务里调用 {@link CombatArtEffectManager#spawn}——该方法只操作普通列表、
     * 不引用任何客户端专有渲染类，双端加载安全（与项目现有 {@code AoeEffectPacket} 一致），
     * 因此无需 {@code DistExecutor} 包裹。这样可避免在 Mohist 等混合端因
     * 「服务端引用 {@code @OnlyIn(CLIENT)} 类」而引发的类加载问题。
     * </p>
     *
     * @param packet 收到的包
     * @param ctx    网络上下文
     */
    public static void handle(CombatArtEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                CombatArtEffectManager.spawn(packet.type, packet.x, packet.y, packet.z,
                        packet.radius, packet.yaw));
        ctx.get().setPacketHandled(true);
    }
}
