package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.carianstyle.visual.client.AoeEffectManager;

import java.util.function.Supplier;

/**
 * 定点 / 跟随 AOE 自绘特效触发包（S2C，服务端 -> 客户端）。
 * <p>
 * 这是为「无 MobEffect 的瞬时 AOE 效果」单独铺设的轻量触发链：服务端在附魔触发点
 * 调用 {@link pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles}，由其
 * 通过本包把「效果类型 + 世界坐标 + 半径 + 绑定实体 id」广播给附近客户端；客户端收到后创建一个带
 * 生命周期的自绘几何特效（纯顶点绘制，无贴图、无原版粒子）。
 * </p>
 * <p>
 * 与 {@code ClientSyncEffectPacket}（按实体 id 同步持续状态）不同：本包是<b>一次性</b>的，
 * 不维护实体集合、不需要重同步——特效在客户端按 age 自行播放并到期销毁。
 * </p>
 * <p>
 * <b>v2（跟随）：</b>新增 {@link #entityId} 字段。{@code -1} 表示「定点」（特效锁死在发包坐标，
 * 历史行为，因果律 / 冻结地震 / 排斥等沿用）；{@code >=0} 表示「跟随」（客户端每帧取该实体的插值
 * 实时位置作为特效中心，用于猩红立体花 / 癫火扩散等绑定濒死实体的死亡演出，实体死亡 / 移除后由
 * 客户端回退到最后已知坐标继续播放剩余演出）。为保持对现有大量 5 参调用点的兼容，保留 5 参构造
 * （{@code entityId} 默认 {@code -1}），新增 6 参构造。
 * </p>
 * <p>
 * <b>v3（龙雷红色闪电）：</b>新增特效类型 {@link #TYPE_RED_LIGHTNING}。用于古龙雷击 / 维克的龙雷
 * 替代原版蓝白闪电的视觉——客户端用纯顶点自绘一道竖直红色之字闪电柱（含蜿蜒主干 + 分叉 + 落地红色
 * 冲击环）。该类型为定点特效（不跟随），通过 5 参构造发包即可。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
public class AoeEffectPacket {

    // ===== 效果类型（与客户端 AoeEffectManager / AoeEffectRenderer 的分发严格一致）=====
    /** 因果律：六芒星法阵 + 因果之线 */
    public static final int TYPE_CAUSALITY = 0;
    /** 冻结地震：放射地裂 + 霜环 + 中心冰花 */
    public static final int TYPE_FROST_QUAKE = 1;
    /** 排斥：双环猛烈外推 */
    public static final int TYPE_REPULSION = 2;
    /** 猩红罗妮亚：玫瑰花绽放 -> 炸裂 */
    public static final int TYPE_SCARLET_BLOOM = 3;
    /** 癫火蔓延：混乱裂纹 + 颤动黄橙焰 */
    public static final int TYPE_FRENZIED_FLAME = 4;
    /** 通用回退：中性扩张双环（兼容层未匹配到专属类型时使用） */
    public static final int TYPE_GENERIC = 5;
    /** 龙雷红色闪电：竖直红色之字电柱 + 分叉 + 落地红色冲击（古龙雷击 / 维克的龙雷专用） */
    public static final int TYPE_RED_LIGHTNING = 6;

    /** 「不跟随」哨兵值：entityId 为该值时特效锁死在发包坐标 */
    public static final int NO_ENTITY = -1;

    /** 效果类型（见上方常量） */
    private final int type;
    /** 世界坐标 X */
    private final double x;
    /** 世界坐标 Y（特效贴地的基准高度，通常为触发实体脚底） */
    private final double y;
    /** 世界坐标 Z */
    private final double z;
    /** 半径（格）：决定自绘几何的整体尺寸 */
    private final float radius;
    /** 绑定实体 id；{@link #NO_ENTITY}(-1) 表示定点不跟随，{@code >=0} 表示跟随该实体的实时位置 */
    private final int entityId;

    /**
     * 定点构造（不跟随，历史签名）。{@code entityId} 取 {@link #NO_ENTITY}。
     *
     * @param type   效果类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     */
    public AoeEffectPacket(int type, double x, double y, double z, float radius) {
        this(type, x, y, z, radius, NO_ENTITY);
    }

    /**
     * 完整构造（可指定跟随实体）。
     *
     * @param type     效果类型
     * @param x        世界坐标 X（作为初始坐标 / 实体消失后的回退坐标）
     * @param y        世界坐标 Y
     * @param z        世界坐标 Z
     * @param radius   半径（格）
     * @param entityId 绑定实体 id；{@link #NO_ENTITY}(-1) 为定点，{@code >=0} 为跟随
     */
    public AoeEffectPacket(int type, double x, double y, double z, float radius, int entityId) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.entityId = entityId;
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

    public int getEntityId() {
        return entityId;
    }

    /**
     * 编码。
     * <p>{@code entityId} 用 {@code writeInt} 定长 4 字节：定点特效（entityId=-1）是多数场景，
     * 负数走 VarInt 反而恒占 5 字节，定长更省且实现更简单。</p>
     *
     * @param packet 待编码包
     * @param buf    目标缓冲
     */
    public static void encode(AoeEffectPacket packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.type);
        buf.writeDouble(packet.x);
        buf.writeDouble(packet.y);
        buf.writeDouble(packet.z);
        buf.writeFloat(packet.radius);
        buf.writeInt(packet.entityId);
    }

    /**
     * 解码。
     *
     * @param buf 源缓冲
     * @return 解码出的包
     */
    public static AoeEffectPacket decode(FriendlyByteBuf buf) {
        int type = buf.readByte();
        double x = buf.readDouble();
        double y = buf.readDouble();
        double z = buf.readDouble();
        float radius = buf.readFloat();
        int entityId = buf.readInt();
        return new AoeEffectPacket(type, x, y, z, radius, entityId);
    }

    /**
     * 处理（仅客户端执行：本包为 S2C）。
     * <p>
     * 直接在主线程任务里调用 {@link AoeEffectManager#spawn(int, double, double, double, float, int)}
     * ——该方法只操作普通列表、不引用任何客户端专有渲染类，双端加载安全（与项目现有
     * {@code ClientSyncEffectPacket} 一致），因此无需 {@code DistExecutor} 包裹。这样可避免在
     * Mohist 等混合端因「服务端引用 {@code @OnlyIn(CLIENT)} 类」而引发的类加载问题。
     * </p>
     *
     * @param packet 收到的包
     * @param ctx    网络上下文
     */
    public static void handle(AoeEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                AoeEffectManager.spawn(packet.type, packet.x, packet.y, packet.z, packet.radius, packet.entityId));
        ctx.get().setPacketHandled(true);
    }
}
