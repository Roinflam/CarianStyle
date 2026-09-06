package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.carianstyle.visual.client.AoeEffectManager;

import java.util.function.Supplier;

/**
 * 定点 / 跟随 AOE 自绘特效触发包（S2C，服务端 -> 客户端）。
 * <p>
 * 这是为「无 MobEffect 的瞬时 AOE 效果」单独铺设的轻量触发链：服务端在附魔触发点
 * 调用 {@link pers.roinflam.carianstyle.visual.effect.CarianStyleEffects}，由其
 * 通过本包把「效果类型 + 世界坐标 + 半径 + 绑定实体 id + 可选时长」广播给附近客户端；
 * 客户端收到后创建一个带生命周期的自绘几何特效（纯顶点绘制，无贴图、无原版粒子）。
 * </p>
 * <p>
 * 与 {@code ClientSyncEffectPacket}（按实体 id 同步持续状态）不同：本包是<b>一次性</b>的，
 * 不维护实体集合、不需要重同步——特效在客户端按 age 自行播放并到期销毁。
 * </p>
 * <p>
 * <b>v2（跟随）：</b>新增 {@link #entityId} 字段。{@code -1} 表示「定点」（特效锁死在发包坐标，
 * 因果律 / 冻结地震 / 排斥等沿用）；{@code >=0} 表示「跟随」（客户端每帧取该实体的插值
 * 实时位置作为特效中心，用于猩红立体花 / 癫火扩散等绑定濒死实体的死亡演出）。
 * </p>
 * <p>
 * <b>v3（龙雷红色闪电）：</b>新增特效类型 {@link #TYPE_RED_LIGHTNING}。
 * </p>
 * <p>
 * <b>v4（满月 / 神圣净化）：</b>新增 {@link #TYPE_MOON_BLESSING} 与 {@link #TYPE_SACRED_PURGE}。
 * </p>
 *
 * <h3>v5：新增可选时长字段 {@link #durationMs}</h3>
 * <p>
 * <b>为什么需要它：</b>此前特效时长完全由客户端的
 * {@code AoeEffectManager.durationFor(type)} 按类型写死，这对绝大多数演出都没问题——
 * 因果律、冻结地震、排斥、龙雷的机制时长本来就是固定的。
 * </p>
 * <p>
 * 但<b>满月月华</b>不是：{@code EnchantmentFullMoon} 的回血持续时间取决于持有者
 * <b>有没有装备暗月</b>——不带是 200 tick(10 秒)，带是 400 tick(20 秒)。
 * 客户端无从得知这一点，于是只能取最长的 20 秒写死，
 * 结果不带暗月时特效比实际回血多播 10 秒，玩家早就满血了月光还在头顶挂着。
 * </p>
 * <p>
 * 现在服务端把<b>实际时长</b>随包发下来，客户端照着播，二者严格对齐。
 * </p>
 * <p>
 * <b>兼容性：</b>{@link #AUTO_DURATION}(-1) 表示「按类型用默认时长」，
 * 全部既有调用点走 5 参 / 6 参构造时自动填入该值，行为与 v4 完全一致——
 * <b>不需要改动任何既有演出</b>。只有确实需要动态时长的演出才传具体毫秒数。
 * </p>
 * <p>
 * <b>⚠ 新增类型时只能在末尾追加常量，数值不可插队</b>——{@link #encode} 用
 * {@code writeByte} 写类型，新旧端对同一数值的解读必须一致，插队会导致所有后续类型错位。
 * 当前已用到 8，{@code byte} 的容量（最多 127）远未触及。
 * </p>
 *
 * <h3>v5.1：解码期数值校验</h3>
 * <p>
 * 本包的解码不含「按长度分配容器」的模式，因此没有内存耗尽风险；
 * 需要防的是<b>非法数值流进渲染层</b>：
 * </p>
 * <ul>
 *   <li>{@code radius} 若为 {@code NaN} 或无穷大，客户端不会崩，
 *       但会画出整屏乱纹或干脆什么都不画，而且从现象完全反查不到「某个包里的一个 float」；</li>
 *   <li>{@code radius} 若是个巨大的正数，{@code AoeEffectManager} 里的
 *       {@code scaleFor} 放大后会得到一个覆盖整个可视范围的几何体，等同于一次显卡拒绝服务；</li>
 *   <li>{@code durationMs} 若是个巨大值，该特效会永远留在 {@code ACTIVE} 列表里
 *       （虽有 {@code MAX_ACTIVE} 上限兜底，但会持续占用绘制预算）；</li>
 *   <li>{@code type} 为负数时不对应任何已定义类型，应尽早拒绝而非交给渲染层分发。</li>
 * </ul>
 * <p>
 * 上述校验全部在 {@link #decode} 完成，<b>线格式没有变化</b>，新旧端可互通。
 * 各上限取的都是「正常游戏绝不可能触及」的量级，因此不会影响任何既有演出。
 * </p>
 *
 * @author RoinFlam
 * @version 5.1
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
    /**
     * 满月月华：头顶球形月轮 + 自上而下的月华柱 + 脚下向内收拢的回春环 + 月尘上升。
     * <p>满月濒死复活时的回血演出，<b>跟随持有者</b>，且<b>时长由服务端按实际回血时间指定</b>
     * （详见 {@link #durationMs}）。</p>
     */
    public static final int TYPE_MOON_BLESSING = 7;
    /**
     * 神圣净化：金色十字光刃爆闪 + 净化环 + 升天光尘。
     * <p>神圣刀刃击中亡灵时的瞬时反馈，<b>定点</b>。</p>
     */
    public static final int TYPE_SACRED_PURGE = 8;

    /** 「不跟随」哨兵值：entityId 为该值时特效锁死在发包坐标 */
    public static final int NO_ENTITY = -1;

    /**
     * 「按类型用默认时长」哨兵值。
     * <p>{@link #durationMs} 取该值时，客户端使用
     * {@code AoeEffectManager.durationFor(type)} 的返回值——这是全部既有演出的行为。</p>
     */
    public static final int AUTO_DURATION = -1;

    // ===== v5.1 校验上限（正常游戏绝不会触及）=====

    /**
     * 允许的最大类型编号。
     * <p>比当前最大值 {@link #TYPE_SACRED_PURGE}(8) 留出余量，
     * 这样以后追加新类型时不必同步改这里；但仍能挡住 {@code byte} 能表达的全部负数与远端垃圾值。</p>
     */
    private static final int MAX_TYPE = 63;

    /**
     * 允许的最大半径（格）。
     * <p>模组内最大的演出（黄金树祝福 / 龙雷）半径也在个位数到十几格，
     * 客户端渲染裁剪距离是 48 格，64 已远超任何合理值。</p>
     */
    private static final float MAX_RADIUS = 64.0F;

    /**
     * 允许的最大播放时长（毫秒）。
     * <p>最长的演出（满月 20 秒回血）是 20000ms，60 秒是三倍余量。</p>
     */
    private static final int MAX_DURATION_MS = 60_000;

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
     * 特效播放时长（毫秒）；{@link #AUTO_DURATION}(-1) 表示按类型用客户端默认时长。
     * <p>
     * 供「机制时长本身可变」的演出使用——目前只有满月月华（回血 10 秒或 20 秒，
     * 取决于是否装备暗月）。其余演出一律传 {@link #AUTO_DURATION}。
     * </p>
     */
    private final int durationMs;

    /**
     * 定点构造（不跟随、默认时长，历史签名）。
     *
     * @param type   效果类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     */
    public AoeEffectPacket(int type, double x, double y, double z, float radius) {
        this(type, x, y, z, radius, NO_ENTITY, AUTO_DURATION);
    }

    /**
     * 跟随构造（默认时长，v2 起的历史签名）。
     *
     * @param type     效果类型
     * @param x        世界坐标 X
     * @param y        世界坐标 Y
     * @param z        世界坐标 Z
     * @param radius   半径（格）
     * @param entityId 绑定实体 id；{@link #NO_ENTITY}(-1) 为定点，{@code >=0} 为跟随
     */
    public AoeEffectPacket(int type, double x, double y, double z, float radius, int entityId) {
        this(type, x, y, z, radius, entityId, AUTO_DURATION);
    }

    /**
     * 完整构造（可指定跟随实体与播放时长）。
     *
     * @param type       效果类型
     * @param x          世界坐标 X（作为初始坐标 / 实体消失后的回退坐标）
     * @param y          世界坐标 Y
     * @param z          世界坐标 Z
     * @param radius     半径（格）
     * @param entityId   绑定实体 id；{@link #NO_ENTITY}(-1) 为定点，{@code >=0} 为跟随
     * @param durationMs 播放时长（毫秒）；{@link #AUTO_DURATION}(-1) 为按类型取默认值
     */
    public AoeEffectPacket(int type, double x, double y, double z, float radius,
                           int entityId, int durationMs) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
        this.entityId = entityId;
        this.durationMs = durationMs;
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

    public int getDurationMs() {
        return durationMs;
    }

    /**
     * 编码。
     * <p>{@code entityId} 与 {@code durationMs} 都用 {@code writeInt} 定长 4 字节：
     * 二者的哨兵值都是 -1（负数走 VarInt 反而恒占 5 字节），定长更省且实现更简单。</p>
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
        buf.writeInt(packet.durationMs);
    }

    /**
     * 解码。
     * <p>v5.1：全部数值经边界校验，详见类注释。线格式未变。</p>
     *
     * @param buf 源缓冲
     * @return 解码出的包
     * @throws IllegalArgumentException 类型编号非法时抛出，由 Forge 断开该连接
     */
    public static AoeEffectPacket decode(FriendlyByteBuf buf) {
        int type = buf.readByte();
        if (type < 0 || type > MAX_TYPE) {
            throw new IllegalArgumentException("AoeEffectPacket 的效果类型非法：" + type);
        }

        double x = PacketGuard.sanitizeCoordinate(buf.readDouble());
        double y = PacketGuard.sanitizeCoordinate(buf.readDouble());
        double z = PacketGuard.sanitizeCoordinate(buf.readDouble());

        // 半径为 NaN / 无穷大时回退到 1 格：画一个很小的特效，
        // 比不画更容易在测试中被发现，也不会影响帧率
        float radius = PacketGuard.sanitize(buf.readFloat(), 0.0F, MAX_RADIUS, 1.0F);

        // entityId 只区分「负数即不跟随」，统一归一到哨兵值，
        // 避免 -7 这种值被 AoeEffectManager 当成实体 id 去查
        int entityId = buf.readInt();
        if (entityId < 0) {
            entityId = NO_ENTITY;
        }

        // durationMs：负数一律归一为 AUTO_DURATION（按类型取默认时长），正数收拢到上限
        int durationMs = buf.readInt();
        durationMs = durationMs < 0 ? AUTO_DURATION : PacketGuard.clamp(durationMs, 0, MAX_DURATION_MS);

        return new AoeEffectPacket(type, x, y, z, radius, entityId, durationMs);
    }

    /**
     * 处理（仅客户端执行：本包为 S2C）。
     * <p>
     * 直接在主线程任务里调用
     * {@link AoeEffectManager#spawn(int, double, double, double, float, int, int)}
     * ——该方法只操作普通列表、不引用任何客户端专有渲染类，双端加载安全，
     * 因此无需 {@code DistExecutor} 包裹。这样可避免在 Mohist 等混合端因
     * 「服务端引用 {@code @OnlyIn(CLIENT)} 类」而引发的类加载问题。
     * </p>
     * <p>
     * <b>v5.1 补充：</b>本包已在 {@code VisualNetwork} 注册时声明为
     * {@code NetworkDirection.PLAY_TO_CLIENT}，服务端收到反向包会被 Forge
     * 在分发入口直接拒绝，处理器根本不会进入——这从源头消除了上述类加载顾虑，
     * 比 {@code DistExecutor} 更彻底。
     * </p>
     *
     * @param packet 收到的包
     * @param ctx    网络上下文
     */
    public static void handle(AoeEffectPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                AoeEffectManager.spawn(packet.type, packet.x, packet.y, packet.z,
                        packet.radius, packet.entityId, packet.durationMs));
        ctx.get().setPacketHandled(true);
    }
}
