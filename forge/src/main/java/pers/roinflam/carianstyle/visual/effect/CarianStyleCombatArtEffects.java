package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

/**
 * 服务端战技（COMBAT_SKILL）自绘特效触发入口。
 * <p>
 * 与 {@link CarianStyleEffects} 并列的第二个特效入口类，专供<b>有朝向</b>的战技演出。
 * 本类只负责把「在某位置、朝某方向播放哪种战技演出」广播给附近客户端；真正的视觉由客户端
 * 各战技渲染器用纯顶点几何自绘完成——无贴图、无原版粒子。
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
 * <h3>v1.1：新增水鸟乱舞</h3>
 * <p>
 * 这是本类第一个<b>需要在一次攻击里被调用多次</b>的演出——
 * {@code EnchantmentWaterfowlFlurry} 把攻击拆成 {@code level+1} 段、每 2 tick 一段，
 * 每段各调一次 {@link #waterfowlFlurry}，多道刀光靠各自独立的诞生时刻自然错相成连斩。
 * </p>
 *
 * <h3>v1.2：新增六个战技演出</h3>
 * <p>
 * 不屈壁障、狮子斩、二连斩、箭步上砍、格挡窗口、盾牌冲击。
 * </p>
 * <p>
 * <b>其中三个是「打在别人身上」的</b>——狮子斩、二连斩、箭步上砍都提供了
 * {@code (level, attacker, victim)} 形式的重载：<b>位置取受击者、朝向取攻击者</b>。
 * 这一点看似小，其实是这三个演出成立的前提：爪痕留在被抓的那一方身上、
 * 刀光的平面必须正对着挥刀的人，两者取自不同实体。
 * </p>
 *
 * <h3>v1.3：六个新演出的半径全面放大（实测反馈）</h3>
 * <p>
 * 实测中「狮子斩看不到」「盾牌冲击好像没效果」——排查下来，
 * 视觉尺度是主因之一：v1.2 的半径是照着「贴着身体一圈」定的，
 * 但战斗中玩家的注意力在血条、目标模型和自己的挥击动画上，
 * 一个只有一格多、半秒就没的图形<b>确实会被整个漏掉</b>。
 * </p>
 * <p>
 * 本次六个半径统一放大到 1.3~1.6 倍。放大之后它们仍然明显小于既有的三个战技
 * （居合 4.0 / 回旋 3.0 / 祈祷 3.5），不会喧宾夺主，但已经越过了「能被余光捕捉到」的门槛。
 * </p>
 * <p>
 * <b>注意半径与判定的关系没有变</b>：这六个附魔全都不是 AOE，
 * 半径纯粹是视觉尺度，放大不会让任何玩家误判打击范围
 * （唯一与判定挂钩的是回旋斩的 {@link #SPIN_RADIUS}，那个没动）。
 * </p>
 *
 * @author FlameForge
 * @version 1.3
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
     * 刀光扫到哪里就应该打到哪里，视觉与判定对不上会让玩家误判走位。
     * <b>这是本类唯一与判定挂钩的半径，改动前务必确认附魔那边的数值。</b></p>
     */
    private static final float SPIN_RADIUS = 3.0f;

    /**
     * 祈祷一击默认半径（格）。
     * <p>祈祷一击是单体技，此半径仅为落地金环与地面圣徽的视觉尺度。</p>
     */
    private static final float PRAYER_RADIUS = 3.5f;

    /**
     * 水鸟乱舞默认半径（格）。
     * <p>
     * 与居合同理，水鸟乱舞<b>不是 AOE</b>（每段只打原目标），此半径纯为视觉尺度。
     * 取 3.6 比居合的 4.0 略小——单道弧本身跨度就窄（110°），
     * 再做大会让每一道都像居合、连起来反而看不出是多段。
     * </p>
     */
    private static final float WATERFOWL_RADIUS = 3.6f;

    /**
     * 不屈壁障默认半径（格）。
     * <p>v1.3：1.6 → 2.4。免疫成功是个很重要的正反馈（最高 75% 概率完全免伤），
     * 原来那个尺寸在残血混战时基本看不见。</p>
     */
    private static final float INDOMITABLE_RADIUS = 2.4f;

    /**
     * 狮子斩默认半径（格）。
     * <p>v1.3：1.5 → 2.4。实测反馈「没看到特效」——1.5 格的爪痕在近战贴脸时
     * 会被目标模型和自己的挥剑动画一起吃掉。放大到比人形目标明显大一圈，
     * 爪痕才能盖过目标轮廓被看见。</p>
     */
    private static final float LION_CLAW_RADIUS = 2.4f;

    /**
     * 二连斩默认半径（格）。
     * <p>v1.3：1.6 → 2.5。比狮子斩再大一点点——它是两道交叉的刀光，
     * 交叉点需要足够的展开空间才读得出是 X 而不是一个亮团。</p>
     */
    private static final float DOUBLE_SLASH_RADIUS = 2.5f;

    /**
     * 箭步上砍默认半径（格）。
     * <p>v1.3：2.0 → 2.8。它的上挑弧是竖直方向的，半径同时决定了弧的高度——
     * 放大后弧顶能到目标头顶以上，「把人挑起来」这层意思才出得来。</p>
     */
    private static final float LUNGE_UP_RADIUS = 2.8f;

    /**
     * 格挡窗口默认半径（格）。
     * <p>v1.3：1.3 → 1.9。仍是六个里最小的——它是给持有者自己看的<b>状态提示</b>，
     * 做太大会挡住正要反击的目标。但 1.3 格实在偏小，举盾时几乎糊在盾后面。</p>
     */
    private static final float PARRY_RADIUS = 1.9f;

    /**
     * 盾牌冲击默认半径（格）。
     * <p>v1.3：2.6 → 3.4。实测反馈「好像没效果」——原来是贴地的扇形，
     * 举盾时视线朝前，脚下三格远的一片平面正好在视野死角。
     * 渲染器那边已改为<b>竖直扇面墙</b>为主体，半径同步放大以匹配。</p>
     */
    private static final float SHIELD_BASH_RADIUS = 3.4f;

    /**
     * 水鸟乱舞相邻两段之间的朝向偏移（度）。
     * <p>
     * 段序号为偶数时取 {@code -本值}、奇数时取 {@code +本值}，使各道弧一左一右地交叉。
     * 取 22° 是因为单道跨度 110°：偏移太小（&lt;10°）几道弧几乎重合、看不出多段；
     * 偏移太大（&gt;40°）又会散成互不相干的几刀、失去「一套连招」的整体感。
     * </p>
     */
    private static final float WATERFOWL_YAW_STEP = 22f;

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
     * 祈祷一击：自天而降的金色圣光柱 + 落地扩散金环 + 地面十字圣徽 + 升腾金光丝。
     *
     * @param level  服务端世界
     * @param holder 持有者（光降在祈祷者自己身上，而非目标身上）
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

    // ============================== 水鸟乱舞 ==============================

    /**
     * 水鸟乱舞：一道极窄极快的交叉刀光（银白刀锋 + 猩红边）。
     * <p>
     * <b>这个方法要在一次攻击里被调用多次</b>——{@code EnchantmentWaterfowlFlurry}
     * 的每一段攻击各调一次，{@code segmentIndex} 传该段的序号（第一段传 0）。
     * 多道刀光靠各自独立的诞生时刻自然错相叠加成连斩，
     * 并靠 {@link #WATERFOWL_YAW_STEP} 派生的朝向偏移交叉开来。
     * </p>
     *
     * @param level        服务端世界
     * @param holder       持有者（自动取其位置与朝向）
     * @param segmentIndex 段序号（第一段 0，此后递增），决定该段刀光的交叉方向
     */
    public static void waterfowlFlurry(ServerLevel level, LivingEntity holder, int segmentIndex) {
        float offset = ((segmentIndex & 1) == 0) ? -WATERFOWL_YAW_STEP : WATERFOWL_YAW_STEP;
        send(level, CombatArtEffectPacket.TYPE_WATERFOWL_FLURRY,
                holder.getX(), holder.getY(), holder.getZ(),
                WATERFOWL_RADIUS, holder.getYRot() + offset);
    }

    /**
     * 水鸟乱舞（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，已含段偏移）
     * @param radius 刀光半径（格）
     */
    public static void waterfowlFlurry(ServerLevel level, double x, double y, double z,
                                       float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_WATERFOWL_FLURRY, x, y, z, radius, yaw);
    }

    // ============================== 不屈壁障 ==============================

    /**
     * 不屈壁障：胸口白热爆闪 + 六片向外推开的壁障碎片 + 双层冲击环。
     * <p>
     * <b>只在免疫真正生效的那一刻调用</b>——即
     * {@code EnchantmentIndomitable} 里 {@code evt.setCanceled(true)} 的同一分支。
     * 概率没中的时候不要调，否则视觉就在骗人。
     * </p>
     *
     * @param level  服务端世界
     * @param holder 免疫了这次伤害的持有者
     */
    public static void indomitable(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_INDOMITABLE,
                holder.getX(), holder.getY(), holder.getZ(),
                INDOMITABLE_RADIUS, holder.getYRot());
    }

    /**
     * 不屈壁障（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，决定碎片推开的基准方位）
     * @param radius 半径（格）
     */
    public static void indomitable(ServerLevel level, double x, double y, double z,
                                   float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_INDOMITABLE, x, y, z, radius, yaw);
    }

    // ============================== 狮子斩 ==============================

    /**
     * 狮子斩：三道平行斜切爪痕，划在<b>目标</b>身上。
     * <p>
     * <b>位置取受击者、朝向取攻击者</b>——爪痕留在被抓的那一方身上，
     * 而爪痕所在的平面必须正对着挥爪的人。两者取自不同实体，这是本演出成立的前提。
     * </p>
     *
     * @param level    服务端世界
     * @param attacker 攻击者（只取其朝向）
     * @param victim   受击者（取其位置作为爪痕中心）
     */
    public static void lionClaw(ServerLevel level, LivingEntity attacker, LivingEntity victim) {
        send(level, CombatArtEffectPacket.TYPE_LION_CLAW,
                victim.getX(), victim.getY(), victim.getZ(),
                LION_CLAW_RADIUS, attacker.getYRot());
    }

    /**
     * 狮子斩（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      爪痕中心 X
     * @param y      爪痕中心 Y（受击者脚底）
     * @param z      爪痕中心 Z
     * @param yaw    朝向（度，取攻击者）
     * @param radius 爪痕尺度（格）
     */
    public static void lionClaw(ServerLevel level, double x, double y, double z,
                                float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_LION_CLAW, x, y, z, radius, yaw);
    }

    // ============================== 二连斩 ==============================

    /**
     * 二连斩：两道交叉成 X 形的刀光，第二道错相追上，划在<b>目标</b>身上。
     * <p>位置与朝向的取法同 {@link #lionClaw}。</p>
     *
     * @param level    服务端世界
     * @param attacker 攻击者（只取其朝向）
     * @param victim   受击者（取其位置作为刀光中心）
     */
    public static void doubleSlash(ServerLevel level, LivingEntity attacker, LivingEntity victim) {
        send(level, CombatArtEffectPacket.TYPE_DOUBLE_SLASH,
                victim.getX(), victim.getY(), victim.getZ(),
                DOUBLE_SLASH_RADIUS, attacker.getYRot());
    }

    /**
     * 二连斩（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      刀光中心 X
     * @param y      刀光中心 Y（受击者脚底）
     * @param z      刀光中心 Z
     * @param yaw    朝向（度，取攻击者）
     * @param radius 刀光尺度（格）
     */
    public static void doubleSlash(ServerLevel level, double x, double y, double z,
                                   float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_DOUBLE_SLASH, x, y, z, radius, yaw);
    }

    // ============================== 箭步上砍 ==============================

    /**
     * 箭步上砍：自下而上的上挑弧 + 地面急停尘环 + 顶端击飞火花，作用在<b>目标</b>身上。
     * <p>
     * 位置与朝向的取法同 {@link #lionClaw}。地面尘环画在受击者脚下——
     * 冲刺被硬生生刹住的动量是砸在两人之间的，画在目标脚下比画在攻击者脚下更贴合
     * 「顶着人往上挑」的观感。
     * </p>
     *
     * @param level    服务端世界
     * @param attacker 攻击者（只取其朝向）
     * @param victim   被挑飞的目标（取其位置）
     */
    public static void lungeUp(ServerLevel level, LivingEntity attacker, LivingEntity victim) {
        send(level, CombatArtEffectPacket.TYPE_LUNGE_UP,
                victim.getX(), victim.getY(), victim.getZ(),
                LUNGE_UP_RADIUS, attacker.getYRot());
    }

    /**
     * 箭步上砍（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（目标脚底，尘环贴此高度）
     * @param z      中心 Z
     * @param yaw    朝向（度，取攻击者）
     * @param radius 尺度（格）
     */
    public static void lungeUp(ServerLevel level, double x, double y, double z,
                               float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_LUNGE_UP, x, y, z, radius, yaw);
    }

    // ============================== 格挡窗口 ==============================

    /**
     * 格挡窗口：盾前弹开火星 + 一个随时间收缩的准星（提示「现在反击有加成」）。
     * <p>
     * <b>在成功架住攻击、反击加成被写入的那一刻调用</b>——即
     * {@code EnchantmentParry.onLivingAttack} 里 {@code setData(PARRY_LEVEL_KEY, ...)}
     * 的同一分支，而不是反击真正打出去的时候。玩家需要的是「现在可以反击了」这个<b>预告</b>，
     * 打出去之后再提示就没意义了。
     * </p>
     * <p>
     * 客户端的播放时长（500ms）与附魔那边的 10 tick 数据有效期严格对齐，
     * 准星收缩到零即窗口关闭。<b>改了附魔里那个 10，就要同步改
     * {@code CombatArtEffectManager.PARRY_WINDOW_DURATION_MS}。</b>
     * </p>
     *
     * @param level  服务端世界
     * @param holder 成功格挡的持有者
     */
    public static void parryWindow(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_PARRY_WINDOW,
                holder.getX(), holder.getY(), holder.getZ(),
                PARRY_RADIUS, holder.getYRot());
    }

    /**
     * 格挡窗口（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，决定准星所在平面）
     * @param radius 尺度（格）
     */
    public static void parryWindow(ServerLevel level, double x, double y, double z,
                                   float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_PARRY_WINDOW, x, y, z, radius, yaw);
    }

    // ============================== 盾牌冲击 ==============================

    /**
     * 盾牌冲击：朝正前方推出的竖直扇面冲击墙 + 地面落点扇形 + 推力线。
     * <p>
     * <b>扇形而非整圆</b>——击退只作用于正面的攻击者，形状如实反映这一点。
     * 朝向必须取持有者（盾面朝向），而不是攻击者。
     * </p>
     * <p>
     * <b>⚠ 关于「看起来没效果」：</b>附魔本身靠 {@code attacker.knockback()} 击退，
     * 而<b>铁傀儡的击退抗性是 1.0</b>（凋灵骷髅、远古守卫者等同理），
     * 对它们施加的击退会被完全吸收——这是原版机制，不是附魔或特效的问题。
     * 拿僵尸、骷髅这类无击退抗性的怪测才看得出位移。
     * </p>
     *
     * @param level  服务端世界
     * @param holder 举盾的持有者
     */
    public static void shieldBash(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_SHIELD_BASH,
                holder.getX(), holder.getY(), holder.getZ(),
                SHIELD_BASH_RADIUS, holder.getYRot());
    }

    /**
     * 盾牌冲击（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，扇形以此为中轴）
     * @param radius 冲击半径（格）
     */
    public static void shieldBash(ServerLevel level, double x, double y, double z,
                                  float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_SHIELD_BASH, x, y, z, radius, yaw);
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
