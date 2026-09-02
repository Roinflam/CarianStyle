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
 * 当前已用到 18（其中 11 / 13 / 15 / 16 为已移除类型留下的空号），
 * {@code byte} 的容量（最多 127）远未触及。
 * </p>
 *
 * <h3>v1.2：新增六个战技类型（4~9）</h3>
 * <p>
 * 这一批全部由<b>独立渲染器</b> {@code CombatArtExtraRenderer} 绘制，
 * {@code CombatArtEffectRenderer} <b>一行都不用改</b>——后者的分发 switch 末尾是
 * {@code default -> { }}，会静默跳过自己不认识的类型。
 * </p>
 *
 * <h3>v1.3：新增「数值型附魔」的打击反馈</h3>
 * <p>
 * 这九个附魔此前<b>一点视觉都没有</b>——它们全是「攻击时按条件加伤 / 减伤」的纯数值效果，
 * 玩家除了盯伤害数字之外无法感知它有没有生效。本批补上的正是那个缺口。
 * </p>
 * <p>
 * <b>为什么仍然走本包而不是 {@link AoeEffectPacket}：</b>
 * 除了「它们都需要朝向」之外，更实际的理由是 {@code AoeEffectRenderer} 的分发 switch
 * 末尾是 {@code default -> drawGeneric(...)}——往那边加类型而不改渲染器，
 * 新类型会<b>额外多画一圈通用蓝白环</b>；而改那个文件意味着动一个四千余行的类。
 * 本包这边的 {@code default -> { }} 则天然安全（详见 v1.2 小节）。
 * </p>
 * <p>
 * 这九个由<b>第三个独立渲染器</b> {@code CombatArtBurstRenderer} 绘制，
 * {@code CombatArtEffectRenderer} 与 {@code CombatArtExtraRenderer} 同样一行都不用改。
 * </p>
 * <p>
 * <b>⚠ 高频触发警告：</b>本批里的血刃、复仇誓言、黄金律法、献斗剑、碎星
 * 都是<b>每次攻击</b>就可能触发的，与前两批「概率触发 / 条件触发」的性质不同。
 * {@code CombatArtEffectManager} 已为此加了同位置合并与更高的存活上限，
 * 但服务端侧仍建议不要在每一次伤害事件里无条件发包（详见该类注释）。
 * </p>
 *
 * @author FlameForge
 *
 * <h3>v1.4：移除四个演出</h3>
 * <p>
 * 复仇誓言（11）、战士（13）、碎星（15）、献斗剑（16）四个类型已删除，
 * 编号保留为空号。剩余五个为 10 血刃、12 挥石魔法、14 黄金律法、17 对空射击、18 硬箭。
 * </p>
 *
 * @version 1.4
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
     * 不屈壁障：胸口白热爆闪 + 六片向外推开的壁障碎片 + 双层冲击环。
     * <p>
     * 对应 {@code EnchantmentIndomitable} <b>免疫成功的那一瞬</b>。
     * 该附魔最高 75% 概率完全免疫伤害，但在此之前玩家只能靠「怎么没掉血」去猜——
     * 这个反馈补的正是那个缺口。定点、约 600ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_INDOMITABLE = 4;

    /**
     * 狮子斩：三道平行斜切爪痕（兽金 + 暗棕红），瞬间划过目标。
     * <p>
     * 对应 {@code EnchantmentLionClaw} 的 20% 概率触发（无视护甲 + 增伤）。
     * <b>特效画在目标身上、朝向取攻击者</b>——爪痕是留在被抓的那一方身上的。
     * 定点、约 650ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_LION_CLAW = 5;

    /**
     * 二连斩：两道交叉成 X 形的刀光，第二道错相追上（银白 + 钢蓝）。
     * <p>
     * 对应 {@code EnchantmentDoubleSlash} 的追加攻击。
     * <b>「两道、交叉成 X」是它与居合（单道宽弧）、水鸟（多道窄弧）的区分依据</b>。
     * 特效画在目标身上、朝向取攻击者。定点、约 680ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_DOUBLE_SLASH = 6;

    /**
     * 箭步上砍：自下而上的上挑弧 + 地面急停尘环 + 顶端击飞火花。
     * <p>
     * 对应 {@code EnchantmentLungeUp} 冲刺攻击时的中断蓄力上挑。
     * <b>运动方向向上</b>，与其余全部战技（水平横扫）相反，一眼可辨。
     * 特效画在目标身上、朝向取攻击者。定点、约 750ms。
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
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_PARRY_WINDOW = 8;

    /**
     * 盾牌冲击：朝正前方推出的扇形冲击波 + 三条推力线（钢灰白）。
     * <p>
     * 对应 {@code EnchantmentShieldBash} 举盾受击后的额外击退。
     * <b>扇形而非整圆</b>——击退只作用于正面的攻击者，形状如实反映这一点。
     * 定点、约 550ms。
     * </p>
     * <p>由 {@code CombatArtExtraRenderer} 绘制。</p>
     */
    public static final int TYPE_SHIELD_BASH = 9;

    // ===== 数值型附魔的打击反馈（10 / 12 / 14 / 17 / 18），由 CombatArtBurstRenderer 绘制 =====
    // ⚠ 11 / 13 / 15 / 16 是空号：复仇誓言、战士、碎星、献斗剑四个演出已在 v1.4 移除。
    // 刻意不把后面的编号往前挪 —— 重编号会让新旧版本在服务端与客户端不一致时
    // 把一种特效当成另一种播，而留几个永不发送的空号成本为零。

    /**
     * 血刃：自伤血溅 + 一道朝正前方射出的细长血色新月 + 随之前散的血滴。
     * <p>
     * 对应 {@code EnchantmentBloodBlade}：攻击消耗 15% 最大生命，换取基于当前生命的额外伤害。
     * </p>
     * <p>
     * 原型是《艾尔登法环》的战技<b>《血刃》</b>——割破自身、朝正前方射出一道细长的新月。
     * <b>注意不要与《鲜血斩击》混淆</b>：后者是身周的大范围爆发，
     * 在本模组里是另一个附魔（{@code blood_slash}），那套扩散波的语汇该留给它。
     * 两者的几何区别在于：血刃的新月<b>曲率恒定、整体平移</b>，
     * 而鲜血斩击是以自身为圆心向外扩张。
     * </p>
     * <p>
     * <b>特效画在自己身上而非目标身上</b>——这是「自伤换伤」的附魔，
     * 玩家需要看到的是「我付出了什么」。扣了 15% 血却毫无提示，
     * 最容易导致误判血线送命。
     * </p>
     * <p>
     * <b>⚠ 全部图元压在 0.65 格以下。</b>第一人称相机就在胸口高度、朝正前方，
     * 任何锚在自己身上又立到胸口的东西都会糊住准星。定点、约 700ms。
     * </p>
     */
    public static final int TYPE_BLOOD_BLADE = 10;

    /**
     * 挥石魔法：辉石法阵正儿八经地亮起 → 一块朴素的大石头抡过去 → 法阵碎成紫渣。
     * <p>
     * 对应 {@code EnchantmentWaveStoneMagic}：造成的魔法伤害转为物理伤害并 +50%。
     * </p>
     * <p>
     * <b>这个演出画的是一个梗，不是一个法术。</b>笑点在于法师魔力聚了半天、
     * 掏出来的是块石头，而且比法术好使——「挥石」是抡石头，「魔法」是硬贴上去的。
     * 因此视觉的全部重心是<b>反差</b>：法阵做得一板一眼，石头做得灰扑扑毫无光效。
     * </p>
     * <p>
     * <b>⚠ 石头绝对不能加光晕。</b>只要给它一点光效就变成「石属性法术弹」，
     * 梗当场消失。这是本演出唯一不能妥协的一条。
     * </p>
     * <p>位置取受击者、朝向取攻击者。定点、约 580ms。</p>
     */
    public static final int TYPE_WAVE_STONE = 12;

    /**
     * 黄金律法：胸前立起一面矩形黄金律法碑 + 碑面刻纹 + 外扩金环。
     * <p>
     * 对应 {@code EnchantmentGoldenLaw} 的<b>免疫触发</b>那一段
     * （免疫不超过 15% 最大生命的伤害；每 5 秒免疫一次伤害）。
     * </p>
     * <p>
     * <b>「矩形碑面」在全模组独一份</b>——其余演出全是圆、环、星、弧、锥，
     * 没有任何一个用过规整的矩形。矩形天然读作「碑」「律条」「不可逾越的界」，
     * 与黄金律法的语义完全吻合，也与同为金色的祈祷一击（竖直光柱 + 十字）、
     * 神圣净化（三维十字）、黄金树祝福（根须 + 落叶）在形状上彻底分开。
     * </p>
     * <p>
     * <b>只在免疫真正生效的那一刻调用</b>——常驻的增伤 / 减伤没有触发点，
     * 也不该有视觉，否则会变成一个每帧都在闪的噪音源。定点、约 650ms。
     * </p>
     */
    public static final int TYPE_GOLDEN_LAW = 14;

    /**
     * 对空射击：一道自更高处竖直贯下的箭光 + <b>目标高度处</b>的空爆环 + 脚下下压尘环。
     * <p>
     * 对应 {@code EnchantmentSkyShot}：射击高于自身至少 5 格的目标时额外加伤。
     * </p>
     * <p>
     * <b>爆环画在目标所在高度而非地面</b>，这是它与其余全部演出的关键区别——
     * 本模组此前所有的环都贴地（或贴在脚下 / 胸口），只有本演出的主环悬在半空。
     * 因为对空射击的语义正是「在空中把它打下来」，环若落到地面就完全不成立了。
     * </p>
     * <p>特效画在目标身上、朝向取射手。定点、约 800ms。</p>
     */
    public static final int TYPE_SKY_SHOT = 17;

    /**
     * 硬箭：沿箭矢飞行方向张开的锥形贯穿激波 + 命中点四道龟裂。
     * <p>
     * 对应 {@code EnchantmentHardArrow}：弓箭伤害 +[等级]×80%。
     * </p>
     * <p>
     * <b>「锥形」在全模组独一份</b>——盾牌冲击是扇形（竖直墙面）、
     * 排斥是同心环，都不是沿飞行方向逐渐张开的锥。用锥形表达
     * 「一支箭把空气捅穿了」，比再画一圈环要贴切得多。
     * </p>
     * <p>
     * <b>它的代价（12 格内有生物时受伤 +80%×等级）不在这里表达</b>——
     * 那是持续状态，由客户端的 {@code HardArrowRangeRenderer} 画一个
     * 只有自己看得见的 12 格范围光环来提示，与本包无关。
     * </p>
     * <p>特效画在目标身上、朝向取射手。定点、约 600ms。</p>
     */
    public static final int TYPE_HARD_ARROW = 18;

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
     * 以及 v1.2 / v1.3 新增演出所在平面的朝向。
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
