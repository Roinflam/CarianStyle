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
 * <b>与 {@code AoeEffectPacket} 的其它差异：</b>战技特效都是<b>短促瞬时</b>的（0.4~1 秒），
 * 不支持跟随实体（刀光挥出去就固定在挥出时的位置，跟着人走反而不合理），
 * 也没有分段进度映射（无需对齐延迟触发的第二阶段）。
 * </p>
 * <p>
 * <b>⚠ 新增类型时只能在末尾追加常量，数值不可插队</b>——{@link #encode} 用
 * {@code writeByte} 写类型，新旧端对同一数值的解读必须一致，插队会导致所有后续类型错位。
 * 当前已用到 9，{@code byte} 的容量（最多 127）远未触及。
 * </p>
 *
 * <h3>v1.2：新增六个战技类型（4~9）</h3>
 * <p>
 * 这一批全部由<b>独立渲染器</b> {@code CombatArtExtraRenderer} 绘制，
 * {@code CombatArtEffectRenderer} <b>一行都不用改</b>——后者的分发 switch 末尾是
 * {@code default -> { }}，会静默跳过自己不认识的类型。
 * </p>
 * <p>
 * <b>为什么这六个走本包而不是 {@link AoeEffectPacket}：</b>两条理由。
 * 其一，它们在 {@code @AutoRegisterEnchantment} 里全部标注为
 * {@code EnchantmentCategory.COMBAT_SKILL}，语义上本就属于战技。
 * 其二也是更关键的——{@code AoeEffectRenderer} 的分发 switch 末尾是
 * {@code default -> drawGeneric(...)}，往那边加类型而不改渲染器的话，
 * 新类型会<b>额外多画一圈通用蓝白环</b>；而改那个文件意味着动一个四千余行的类。
 * 本包这边则完全没有这个问题。
 * </p>
 *
 * @author FlameForge
 * @version 1.2
 */
public class CombatArtEffectPacket {

    // ===== 效果类型（与客户端 CombatArtEffectManager / 各渲染器的分发严格一致）=====

    /**
     * 居合斩：沿持有者正面扫出的一道极快水平弧形刀光（银白刀锋 + 残影 + 地面切割线）。
     * <p>对应 {@code EnchantmentUnsheathe} 概率触发的居合斩。
     * 由 {@code CombatArtEffectRenderer} 绘制。</p>
     */
    public static final int TYPE_IAI_SLASH = 0;

    /**
     * 回旋斩：绕自身完整扫过 360° 的环形刀光（银白刀锋 + 琥珀扬尘环）。
     * <p>对应 {@code EnchantmentStampSweep} 冲刺攻击时的箭步回旋斩。
     * 由 {@code CombatArtEffectRenderer} 绘制。</p>
     */
    public static final int TYPE_SPIN_SLASH = 1;

    /**
     * 祈祷一击：自天而降的金色圣光柱 + 落地金环 + 地面十字圣徽。
     * <p>对应 {@code EnchantmentPrayerfulStrike} 蓄力完成后的那一击。
     * 由 {@code CombatArtEffectRenderer} 绘制。</p>
     */
    public static final int TYPE_PRAYER_STRIKE = 2;

    /**
     * 水鸟乱舞：一道极窄极快的交叉刀光（银白刀锋 + 猩红边）。
     * <p>
     * 对应 {@code EnchantmentWaterfowlFlurry}。<b>该附魔每一段攻击各发一个包</b>
     * （共 level+1 段、每 2 tick 一段），因此多道刀光会自然错相叠加成连斩，
     * 不需要在包里携带段数——这也是本包没有「连击数」字段的原因。
     * </p>
     * <p>由独立的 {@code WaterfowlFlurryRenderer} 绘制。</p>
     */
    public static final int TYPE_WATERFOWL_FLURRY = 3;

    /**
     * 不屈壁障：胸口白热爆闪 + 六片向外推开的壁障碎片 + 双层冲击环（v1.2 新增）。
     * <p>
     * 对应 {@code EnchantmentIndomitable} <b>免疫成功的那一瞬</b>。
     * 该附魔最高 75% 概率完全免疫伤害，但在此之前玩家只能靠「怎么没掉血」去猜——
     * 这个反馈补的正是那个缺口。定点、约 450ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_INDOMITABLE = 4;

    /**
     * 狮子斩：三道平行斜切爪痕（兽金 + 暗棕红），瞬间划过目标。
     * <p>
     * 对应 {@code EnchantmentLionClaw} 的 20% 概率触发（无视护甲 + 增伤）。
     * <b>特效画在目标身上、朝向取攻击者</b>——爪痕是留在被抓的那一方身上的。
     * 定点、约 500ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_LION_CLAW = 5;

    /**
     * 二连斩：两道交叉成 X 形的刀光，第二道错相追上（银白 + 钢蓝）。
     * <p>
     * 对应 {@code EnchantmentDoubleSlash} 的追加攻击。
     * <b>「两道、交叉成 X」是它与居合（单道宽弧）、水鸟（多道窄弧）的区分依据</b>。
     * 特效画在目标身上、朝向取攻击者。定点、约 520ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_DOUBLE_SLASH = 6;

    /**
     * 箭步上砍：自下而上的上挑弧 + 地面急停尘环 + 顶端击飞火花。
     * <p>
     * 对应 {@code EnchantmentLungeUp} 冲刺攻击时的中断蓄力上挑。
     * <b>运动方向向上</b>，与其余全部战技（水平横扫）相反，一眼可辨。
     * 特效画在目标身上、朝向取攻击者。定点、约 600ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_LUNGE_UP = 7;

    /**
     * 格挡窗口：盾前弹开火星 + 一个随时间收缩的准星（提示「现在反击有加成」）。
     * <p>
     * 对应 {@code EnchantmentParry} <b>成功架住攻击、进入 0.5 秒反击窗口</b>的那一刻。
     * 时长刻意与附魔里 {@code setData(..., 10)} 的 10 tick 严格对齐，
     * 准星收缩到零的瞬间就是窗口关闭的瞬间。定点、500ms。
     * </p>
     * <p>
     * <b>为什么定点而不跟随：</b>本包不支持绑定实体，而举盾时原版会大幅削减移速，
     * 500ms 内位移不足半格，定点完全够用；为此改包格式、动到全部既有构造点，
     * 收益不成比例。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_PARRY_WINDOW = 8;

    /**
     * 盾牌冲击：朝正前方推出的扇形冲击波 + 三条推力线（钢灰白）。
     * <p>
     * 对应 {@code EnchantmentShieldBash} 举盾受击后的额外击退。
     * <b>扇形而非整圆</b>——击退只作用于正面的攻击者，形状如实反映这一点。
     * 定点、约 420ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_SHIELD_BASH = 9;

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
     * <p>决定居合斩弧光扫过的方位、回旋斩的起始角、水鸟乱舞每一段的交叉角，
     * 以及 v1.2 新增六个演出所在平面的朝向。
     * 对朝向无关的演出（如祈祷一击的光柱）该值仅用于让不同次触发的细节
     * （火花分布等）略有差异，不影响主体形态。</p>
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
