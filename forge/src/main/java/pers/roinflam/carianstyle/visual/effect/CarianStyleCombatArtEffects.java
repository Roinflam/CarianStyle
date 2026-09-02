package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.network.CombatArtEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

/**
 * 服务端战技（COMBAT_SKILL）自绘特效触发入口。
 * <p>
 * 与 {@link CarianStyleEffects} 并列的第二个特效入口类，专供<b>有朝向</b>的战技演出。
 * 本类只负责把「在某位置、朝某方向播放哪种演出」广播给附近客户端；真正的视觉由客户端
 * 各渲染器用纯顶点几何自绘完成——无贴图、无原版粒子。
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
 * <h3>v1.1 / v1.2 / v1.3：既有的十个战技</h3>
 * <p>居合、回旋、祈祷、水鸟、不屈、狮子斩、二连斩、箭步上砍、格挡窗口、盾牌冲击。</p>
 *
 * <h3>v1.5：五个「数值型附魔」的打击反馈</h3>
 * <p>
 * 血刃、挥石魔法、黄金律法、对空射击、硬箭。这些此前<b>一点视觉都没有</b>——
 * 全是「攻击时按条件加伤 / 减伤」的纯数值效果，玩家除了盯伤害数字之外无法感知它有没有生效。
 * </p>
 * <p>
 * <b>v1.5 移除了复仇誓言、战士、碎星、献斗剑四个演出。</b>同屏演出过多本身就是干扰，
 * 而这四个要传达的信息（周围敌人数、流血剩余、增减伤档位、激活状态）
 * 已经由 {@code CarianStyleCombatStateDisplay} 的 HUD 表达得更准确、更持续，
 * 世界视觉再画一遍只是重复。
 * </p>
 *
 * <h4>⚠ 调用时机比前几批更需要小心</h4>
 * <p>
 * 前十个战技全是「概率触发」或「特定动作触发」，调用点天然唯一；
 * 而这批大多是<b>条件判定</b>，必须<b>只在条件命中的那一个分支里调</b>：
 * </p>
 * <ul>
 *     <li>{@link #goldenLaw} —— <b>只在免疫真正生效时</b>调（即取消伤害的那一行旁边），
 *         常驻的增伤 / 减伤没有触发点也不该有视觉；</li>
 *     <li>{@link #waveStone} —— 只在<b>魔法伤害判定通过</b>时调；</li>
 *     <li>{@link #skyShot} —— 只在<b>高度差判定通过</b>时调。</li>
 * </ul>
 * <p>
 * 条件没命中却发包，视觉就在骗人——那比没有视觉更糟。
 * </p>
 *
 * <h4>⚠ 三个「打在别人身上」的演出</h4>
 * <p>
 * {@link #waveStone}、{@link #skyShot}、{@link #hardArrow} 都提供了
 * {@code (level, attacker, victim)} 形式的重载：<b>位置取受击者、朝向取攻击者</b>。
 * 这是这些演出成立的前提——碎石要从被砸的那一方身上崩出来，
 * 而崩开的方向必须由攻击者决定。
 * </p>
 *
 * <h4>⚠ 自身锚定的演出必须贴地</h4>
 * <p>
 * {@link #bloodBlade} 与 {@link #goldenLaw} 画在<b>持有者自己</b>身上。
 * 第一人称相机就在胸口高度、朝正前方，<b>任何锚在自己身上又立到胸口的几何体都会糊住准星</b>。
 * 客户端渲染器已把这两个演出的全部图元压在 0.65 格以下、主体铺在地面上；
 * 若以后再加自身锚定的演出，必须遵守同一条约束。
 * </p>
 *
 * <h4>⚠ 高频触发的客户端节流</h4>
 * <p>
 * 血刃、复仇誓言、黄金律法、献斗剑、碎星、挥石魔法都是<b>每次攻击就可能触发</b>的。
 * {@code CombatArtEffectManager} 已为这六个类型加了<b>同位置合并</b>
 * （1.2 格内同类型只续命、不新建），因此即便每次伤害事件都调用也不会把存活上限打穿。
 * </p>
 * <p>
 * 但那只是客户端兜底——<b>服务端侧的包量并没有省</b>。若你的服务器人数较多，
 * 建议在附魔侧自行加一个「同一玩家 N tick 内只发一次」的节流
 * （可照抄 {@code CarianStyleEffects.shouldEmitRedLightning} 的写法）。
 * </p>
 *
 * @author FlameForge
 * @version 1.7
 */
public final class CarianStyleCombatArtEffects {

    /** 特效广播范围（格）：只有该范围内的客户端会收到 */
    private static final double BROADCAST_RANGE = 48.0;

    // ==================== 既有十个战技的半径 ====================

    /**
     * 居合斩默认半径（格）。
     * <p>取 4.0 是因为居合斩本身<b>不是 AOE</b>（只对单一目标造成 ×3.3 伤害），
     * 这个半径纯粹是刀光的视觉尺度。</p>
     */
    private static final float IAI_RADIUS = 4.0f;

    /**
     * 箭步回旋斩默认半径（格）。
     * <p><b>刻意与附魔实际 AOE 半径 3.0 保持一致</b>——回旋斩是真范围技，
     * 刀光扫到哪里就应该打到哪里。<b>这是本类唯一与判定挂钩的半径，
     * 改动前务必确认附魔那边的数值。</b></p>
     */
    private static final float SPIN_RADIUS = 3.0f;

    /** 祈祷一击默认半径（格）。单体技，此半径仅为落地金环与地面圣徽的视觉尺度。 */
    private static final float PRAYER_RADIUS = 3.5f;

    /** 水鸟乱舞默认半径（格）。单道弧跨度本就窄（110°），故比居合略小。 */
    private static final float WATERFOWL_RADIUS = 3.6f;

    /** 不屈壁障默认半径（格） */
    private static final float INDOMITABLE_RADIUS = 2.4f;

    /** 狮子斩默认半径（格） */
    private static final float LION_CLAW_RADIUS = 2.4f;

    /** 二连斩默认半径（格） */
    private static final float DOUBLE_SLASH_RADIUS = 2.5f;

    /** 箭步上砍默认半径（格） */
    private static final float LUNGE_UP_RADIUS = 2.8f;

    /** 格挡窗口默认半径（格）。它是给持有者自己看的状态提示，做太大会挡住反击目标。 */
    private static final float PARRY_RADIUS = 1.9f;

    /** 盾牌冲击默认半径（格） */
    private static final float SHIELD_BASH_RADIUS = 3.4f;

    /**
     * 水鸟乱舞相邻两段之间的朝向偏移（度）。
     * <p>段序号为偶数时取 {@code -本值}、奇数时取 {@code +本值}，使各道弧一左一右地交叉。</p>
     */
    private static final float WATERFOWL_YAW_STEP = 22f;

    // ==================== v1.4 新增九个的半径 ====================
    // 尺度定标依据：这几个全都不是 AOE，半径纯粹是视觉尺度。
    //
    // v1.6 整体下调约三成（原为 2.5~3.0，现为 1.7~2.4）。原值是照着前两批战技
    // （不屈 / 狮子斩，2.4~3.4）定的，但那批是低频的概率触发，而这批每次攻击都可能出现，
    // 同样的尺寸在连续战斗中会显得臃肿；血刃与黄金律法改成贴地之后更是如此——
    // 地面图形不受身高遮挡，整个圆都直接暴露在视野里，观感比同尺寸的竖直图形大得多。
    //
    // 下限守住 1.7：低于这个数在十格外就分辨不出形状了，等于白画。

    /**
     * 血刃默认半径（格）。
     * <p>v1.6 由 2.6 收到 {@value}。改成贴地演出之后，血环与溅射线是<b>摊平在地上</b>的，
     * 同样的半径在观感上比竖直图形大得多——地面图形不受身高遮挡，整个圆都直接暴露在视野里。
     * 溅射线最远到 1.15 倍半径，即约 2.1 格，刚好罩住自己脚下一圈而不铺到别人身上。</p>
     */
    private static final float BLOOD_BLADE_RADIUS = 1.8f;

    /**
     * 挥石魔法默认半径（格）。
     * <p>v1.6 由 2.8 收到 {@value}。钝击横弧仍比刀光类粗，但近战交火时目标就在两格外，
     * 2.8 格的弧带会直接横跨整个视野宽度。收到 {@value} 后弧带贴着目标身形走，
     * 「抡」的分量靠<b>粗细</b>而不是靠尺寸来表达。</p>
     */
    private static final float WAVE_STONE_RADIUS = 1.9f;

    /**
     * 黄金律法默认半径（格）。
     * <p>v1.6 由 2.5 收到 {@value}。地面碑文的长边是半径的 0.92 倍 × 2 ≈ 3.1 格，
     * 原来的 2.5 会摊出一块 4.6 格长的石板——比玩家自己大三倍，站在上面像块地毯。
     * 收到 {@value} 后长约 3 格，与人形的比例才像「脚下一块碑」。</p>
     */
    private static final float GOLDEN_LAW_RADIUS = 1.7f;

    /**
     * 对空射击默认半径（格）。
     * <p>
     * v1.6 由 3.0 收到 {@value}——<b>本批里收得最少的一个</b>。
     * 它的爆环还要再乘 1.35 的放大系数，实际半径约 3.2 格；
     * 而这个演出发生在十几格外的高空，收太狠会退回「看不见」的老问题。
     * </p>
     * <p>缩小主要靠削减那些<b>向外延伸</b>的部分（尖刺长度、光柱长度、箭光起始高度），
     * 而不是削爆环本身——前者是体积，后者是可见性。</p>
     */
    private static final float SKY_SHOT_RADIUS = 2.4f;

    /**
     * 硬箭的<b>满档</b>半径（格）。
     * <p>
     * v1.7 起这不再是固定值，而是{@linkplain #hardArrow(ServerLevel, LivingEntity, Entity, float)
     * 随伤害缩放}的上限：只有当这一箭打掉目标最大生命的
     * {@value #HARD_ARROW_FULL_DAMAGE_RATIO} 时才会用满，轻擦一下只有
     * {@value #HARD_ARROW_MIN_SCALE} 倍。
     * </p>
     * <p>满档时十字冲击臂长 1.05 倍（约 1.9 格），后退环退出 1.4 倍（约 2.5 格）。</p>
     */
    private static final float HARD_ARROW_RADIUS = 1.8f;

    /**
     * 硬箭演出达到满档尺寸所需的伤害占比（占目标<b>最大生命</b>）。
     * <p>
     * 打掉目标 {@value} 的最大生命即用满 {@link #HARD_ARROW_RADIUS}，
     * 更高的伤害不再变大——否则秒杀小怪时会炸出一个盖住半个屏幕的演出。
     * </p>
     * <p>
     * <b>为什么用最大生命而不是当前生命：</b>当前生命会让「补刀一只残血鸡」
     * 也算成 100% 占比、放出满档演出，这显然不对。
     * 最大生命回答的是「我这一下削掉了这个敌人多大一块」，才是想表达的东西。
     * 若要改成当前生命，只需把下方
     * {@code living.getMaxHealth()} 换成 {@code living.getHealth()}。
     * </p>
     */
    private static final float HARD_ARROW_FULL_DAMAGE_RATIO = 0.5f;

    /**
     * 硬箭演出的最小尺寸倍率。
     * <p>
     * 伤害占比为 0 时的尺寸，占满档的 {@value}。
     * <b>刻意不让它趋近于 0</b>——再轻的一箭也该看得见有东西发生，
     * 缩到看不清就等于随机丢帧。
     * </p>
     */
    private static final float HARD_ARROW_MIN_SCALE = 1f / 3f;

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
     * <p><b>只在免疫真正生效的那一刻调用</b>，概率没中时不要调。</p>
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
     * @param yaw    朝向（度）
     * @param radius 半径（格）
     */
    public static void indomitable(ServerLevel level, double x, double y, double z,
                                   float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_INDOMITABLE, x, y, z, radius, yaw);
    }

    // ============================== 狮子斩 ==============================

    /**
     * 狮子斩：三道平行斜切爪痕，划在<b>目标</b>身上。
     * <p><b>位置取受击者、朝向取攻击者</b>。</p>
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
     * <b>在成功架住攻击、反击加成被写入的那一刻调用</b>，而不是反击真正打出去的时候。
     * 客户端的播放时长（500ms）与附魔那边的 10 tick 数据有效期严格对齐。
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
     * <b>⚠ 关于「看起来没效果」：</b>附魔本身靠 {@code attacker.knockback()} 击退，
     * 而<b>铁傀儡的击退抗性是 1.0</b>（凋灵骷髅、远古守卫者等同理），
     * 对它们施加的击退会被完全吸收——这是原版机制，不是附魔或特效的问题。
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

    // ============================== 血刃 ==============================

    /**
     * 血刃：自伤血溅 + 一道朝正前方射出的细长血色新月 + 随之前散的血滴（<b>全部贴地</b>）。
     * <p>
     * 对应 {@code EnchantmentBloodBlade}：攻击消耗 15% 最大生命，换取额外伤害。
     * </p>
     * <p>
     * <b>特效画在自己身上而非目标身上</b>——这是「自伤换伤」的附魔，
     * 玩家需要看到的是「我付出了什么」。扣了 15% 血却毫无提示，
     * 最容易导致误判血线送命，这个反馈补的正是那个缺口。
     * </p>
     * <p>
     * <b>⚠ 全部图元压在 0.65 格以下。</b>第一人称相机就在胸口高度、朝正前方，
     * 任何锚在自己身上又立到胸口的东西都会糊住准星。主体因此铺在地面上，
     * 靠面积和鲜红配色换可见度，而不是靠高度。
     * </p>
     * <p><b>在扣血真正执行的那一行旁边调用</b>，别放在概率判定之前。</p>
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向，朝向决定骨白刃指向）
     */
    public static void bloodBlade(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_BLOOD_BLADE,
                holder.getX(), holder.getY(), holder.getZ(),
                BLOOD_BLADE_RADIUS, holder.getYRot());
    }

    /**
     * 血刃（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，决定新月射出的方向）
     * @param radius 尺度（格）
     */
    public static void bloodBlade(ServerLevel level, double x, double y, double z,
                                  float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_BLOOD_BLADE, x, y, z, radius, yaw);
    }

    // ============================== 挥石魔法 ==============================

    /**
     * 挥石魔法：辉石法阵正儿八经地亮起 → 一块朴素的大石头抡过去 → 法阵碎成紫渣。
     * <p>
     * 对应 {@code EnchantmentWaveStoneMagic}：造成的魔法伤害转为物理伤害并 +50%。
     * </p>
     * <p>
     * <b>这个演出画的是一个梗，不是一个法术。</b>笑点在于法师魔力聚了半天、
     * 掏出来的是块石头，而且比法术好使。视觉的全部重心是<b>反差</b>——
     * 法阵一板一眼，石头灰扑扑没有任何光效。
     * </p>
     * <p>
     * <b>位置取受击者、朝向取攻击者</b>——石头是朝着被砸的那一方抡过去的。
     * </p>
     *
     * @param level    服务端世界
     * @param attacker 攻击者（只取其朝向）
     * @param victim   受击者（取其位置）
     */
    public static void waveStone(ServerLevel level, LivingEntity attacker, LivingEntity victim) {
        send(level, CombatArtEffectPacket.TYPE_WAVE_STONE,
                victim.getX(), victim.getY(), victim.getZ(),
                WAVE_STONE_RADIUS, attacker.getYRot());
    }

    /**
     * 挥石魔法（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（受击者脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，取攻击者；决定石头抡进来的方向）
     * @param radius 尺度（格）
     */
    public static void waveStone(ServerLevel level, double x, double y, double z,
                                 float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_WAVE_STONE, x, y, z, radius, yaw);
    }

    // ============================== 黄金律法 ==============================

    /**
     * 黄金律法：脚下铺开一块矩形黄金律法碑文 + 刻纹 + 外扩金环（<b>全部贴地</b>）。
     * <p>
     * 对应 {@code EnchantmentGoldenLaw} 的<b>免疫触发</b>那一段
     * （免疫不超过 15% 最大生命的伤害；每 5 秒免疫一次伤害）。
     * </p>
     * <p>
     * <b>⚠ 只在免疫真正生效的那一刻调用</b>——即取消伤害的那一行旁边。
     * 常驻的增伤 / 减伤没有触发点，也不该有视觉，否则会变成一个每帧都在闪的噪音源。
     * </p>
     * <p>
     * 「矩形」在全模组独一份，与同为金色的祈祷一击（竖直光柱 + 十字）、
     * 神圣净化（三维十字）、黄金树祝福（根须 + 落叶）在形状上彻底分开。
     * </p>
     * <p>
     * <b>⚠ 碑是放倒铺在地上的，不是立起来的。</b>立起来的版本正好挡住第一人称视野正中，
     * 而免疫恰恰发生在被围殴的时候，那个时机挡视野尤其致命。矩形这个形状语言完整保留。
     * </p>
     *
     * @param level  服务端世界
     * @param holder 免疫了这次伤害的持有者
     */
    public static void goldenLaw(ServerLevel level, LivingEntity holder) {
        send(level, CombatArtEffectPacket.TYPE_GOLDEN_LAW,
                holder.getX(), holder.getY(), holder.getZ(),
                GOLDEN_LAW_RADIUS, holder.getYRot());
    }

    /**
     * 黄金律法（<b>推荐重载</b>：碑面正对伤害来源）。
     * <p>地面碑文沿「持有者 → 攻击者」的方向摆放，读作「挡住了这一下」。</p>
     *
     * @param level    服务端世界
     * @param holder   持有者（取其位置）
     * @param attacker 伤害来源（只用来算碑面朝向）
     */
    public static void goldenLaw(ServerLevel level, LivingEntity holder, Entity attacker) {
        send(level, CombatArtEffectPacket.TYPE_GOLDEN_LAW,
                holder.getX(), holder.getY(), holder.getZ(),
                GOLDEN_LAW_RADIUS, yawTowards(holder, attacker));
    }

    /**
     * 黄金律法（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param yaw    朝向（度，地面碑文沿此方向摆放）
     * @param radius 尺度（格）
     */
    public static void goldenLaw(ServerLevel level, double x, double y, double z,
                                 float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_GOLDEN_LAW, x, y, z, radius, yaw);
    }

    // ============================== 对空射击 ==============================

    /**
     * 对空射击：自更高处竖直贯下的箭光 + <b>目标高度处</b>的大爆环 + 八道径向尖刺
     * + <b>自爆点垂下的光柱</b> + 地面落点环。
     * <p>
     * 对应 {@code EnchantmentSkyShot}：射击高于自身至少 5 格的目标时额外加伤。
     * </p>
     * <p>
     * <b>⚠ 只在高度差判定通过时调用</b>——不满足 5 格高差的普通命中不该有这个演出。
     * </p>
     * <p>
     * <b>爆环画在目标所在高度而非地面</b>，这是它与其余全部演出的关键区别：
     * 对空射击的语义正是「在空中把它打下来」，环若落到地面就完全不成立了。
     * 因此传入的 {@code y} 必须是<b>目标的脚底</b>（在空中时就是空中的高度），
     * 而不是地面高度。
     * </p>
     *
     * @param level   服务端世界
     * @param shooter 射手（只取其朝向）
     * @param victim  被射中的目标（取其位置——注意它在空中）
     */
    public static void skyShot(ServerLevel level, LivingEntity shooter, Entity victim) {
        send(level, CombatArtEffectPacket.TYPE_SKY_SHOT,
                victim.getX(), victim.getY(), victim.getZ(),
                SKY_SHOT_RADIUS, shooter.getYRot());
    }

    /**
     * 对空射击（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（<b>目标脚底，可能在空中</b>）
     * @param z      中心 Z
     * @param yaw    朝向（度，取射手）
     * @param radius 尺度（格）
     */
    public static void skyShot(ServerLevel level, double x, double y, double z,
                               float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_SKY_SHOT, x, y, z, radius, yaw);
    }

    // ============================== 硬箭 ==============================

    /**
     * 硬箭：钉入式十字冲击 + 沿箭道后退张开的冲击环 + 向后散开的火星。
     * <p>
     * 对应 {@code EnchantmentHardArrow}：弓箭伤害 +[等级]×80%。
     * </p>
     * <p>
     * <b>它的代价（12 格内有生物时受伤 +80%×等级）不在这里表达</b>——
     * 那是持续状态，由客户端的 {@code HardArrowRangeRenderer} 画一个
     * <b>只有持有者自己看得见</b>的 12 格范围光环来提示，与本方法无关、
     * 也不需要任何服务端配合。
     * </p>
     * <p>
     * 演出由三部分组成：钉入式十字冲击、一道<b>沿箭道朝射手方向后退并张开的冲击环</b>、
     * 向后锥形散开的火星。三者读法互不干扰，运动方向明确。
     * </p>
     *
     * @param level   服务端世界
     * <p>
     * <b>本重载恒用满档尺寸。</b>常规情况请改用
     * {@link #hardArrow(ServerLevel, LivingEntity, Entity, float)}，
     * 让演出大小跟着伤害走。
     * </p>
     *
     * @param shooter 射手（只取其朝向）
     * @param victim  被射中的目标（取其位置）
     */
    public static void hardArrow(ServerLevel level, LivingEntity shooter, Entity victim) {
        send(level, CombatArtEffectPacket.TYPE_HARD_ARROW,
                victim.getX(), victim.getY(), victim.getZ(),
                HARD_ARROW_RADIUS, shooter.getYRot());
    }

    /**
     * 硬箭（<b>推荐重载</b>：演出尺寸随这一箭的伤害线性缩放）。
     * <p>
     * 缩放公式：
     * </p>
     * <pre>
     * 占比   = 伤害 / 目标最大生命            （夹取到 0~{@value #HARD_ARROW_FULL_DAMAGE_RATIO}）
     * 倍率   = {@value #HARD_ARROW_MIN_SCALE} + (1 - {@value #HARD_ARROW_MIN_SCALE}) × 占比 / {@value #HARD_ARROW_FULL_DAMAGE_RATIO}
     * 半径   = {@value #HARD_ARROW_RADIUS} × 倍率
     * </pre>
     * <p>
     * 也就是说：打掉目标一半最大生命 → 满档；伤害趋近 0 → 三分之一大小；中间线性。
     * </p>
     * <p>
     * <b>为什么值得这么做：</b>硬箭的伤害跨度极大（+80%×等级，高等级可以是基础值的数倍），
     * 而一个固定尺寸的演出既没法表达「这一箭很重」，又会在连射小怪时显得吵。
     * 让尺寸跟着伤害走之后，演出的大小本身就成了一条<b>无需阅读数字的伤害反馈</b>。
     * </p>
     * <p>
     * <b>缩放在这里算完就写进包的 radius 字段</b>，客户端渲染器完全不知情、一行都不用改——
     * 所有几何量本来就是按半径成比例的。
     * </p>
     * <p>
     * 目标不是 {@link LivingEntity}（射中了矿车、盔甲架之类）时无从判断占比，
     * 退回满档尺寸。
     * </p>
     *
     * @param level   服务端世界
     * @param shooter 射手（只取其朝向）
     * @param victim  被射中的目标（取其位置；为 LivingEntity 时用其最大生命算占比）
     * @param damage  这一箭的伤害估值。在 {@code ProjectileImpactEvent} 阶段可用
     *                {@code arrow.getBaseDamage() × 速度模长} 估算——
     *                这正是原版计算箭矢伤害的公式
     */
    public static void hardArrow(ServerLevel level, LivingEntity shooter, Entity victim, float damage) {
        send(level, CombatArtEffectPacket.TYPE_HARD_ARROW,
                victim.getX(), victim.getY(), victim.getZ(),
                HARD_ARROW_RADIUS * hardArrowScale(victim, damage), shooter.getYRot());
    }

    /**
     * 按伤害占目标最大生命的比例，算出硬箭演出的尺寸倍率。
     *
     * @param victim 被射中的目标
     * @param damage 这一箭的伤害估值
     * @return 尺寸倍率，落在 [{@value #HARD_ARROW_MIN_SCALE}, 1]
     */
    private static float hardArrowScale(Entity victim, float damage) {
        if (damage <= 0f || !(victim instanceof LivingEntity living)) {
            return 1f;
        }
        float maxHealth = living.getMaxHealth();
        if (maxHealth <= 0f) {
            return 1f;
        }
        float ratio = damage / maxHealth;
        if (ratio > HARD_ARROW_FULL_DAMAGE_RATIO) {
            ratio = HARD_ARROW_FULL_DAMAGE_RATIO;
        }
        float t = ratio / HARD_ARROW_FULL_DAMAGE_RATIO;
        return HARD_ARROW_MIN_SCALE + (1f - HARD_ARROW_MIN_SCALE) * t;
    }

    /**
     * 硬箭（自定义位置与尺寸）。
     *
     * @param level  服务端世界
     * @param x      命中点 X
     * @param y      命中点 Y（目标脚底）
     * @param z      命中点 Z
     * @param yaw    朝向（度，取射手；冲击环沿其反方向后退）
     * @param radius 尺度（格）
     */
    public static void hardArrow(ServerLevel level, double x, double y, double z,
                                 float yaw, float radius) {
        send(level, CombatArtEffectPacket.TYPE_HARD_ARROW, x, y, z, radius, yaw);
    }

    // ============================== 辅助 ==============================

    /**
     * 算出「从 {@code from} 看向 {@code to}」的水平朝向（度，Minecraft {@code getYRot()} 口径）。
     * <p>
     * 供复仇誓言、战士、黄金律法这几个「朝向必须指向另一个实体」的演出使用——
     * 直接用持有者的视线朝向会指错方向，那几个演出的语义就没了。
     * </p>
     * <p>
     * <b>换算依据：</b>Minecraft 中 yaw=0 面向 +Z、yaw=90 面向 -X，
     * 故 {@code yaw = -atan2(dx, dz)}（度）。
     * 两点水平重合时（理论上不会发生）退回 {@code from} 自身的视线朝向。
     * </p>
     *
     * @param from 起点实体
     * @param to   目标实体
     * @return 水平朝向（度）
     */
    private static float yawTowards(LivingEntity from, Entity to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        if (dx * dx + dz * dz < 1.0e-6) {
            return from.getYRot();
        }
        return (float) (-Math.toDegrees(Math.atan2(dx, dz)));
    }

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
