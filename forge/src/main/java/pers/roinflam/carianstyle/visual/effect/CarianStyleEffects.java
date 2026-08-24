// 文件：CarianStyleEffects.java
// 路径：forge/src/main/java/pers/roinflam/carianstyle/visual/effect/CarianStyleEffects.java
package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 卡利亚式附魔 —— <b>特效统一入口门面</b>（服务端调用）。
 * <p>
 * <b>以后给任何附魔加特效，只调用本类的方法即可。</b>本类是全模组自绘特效的唯一推荐入口，
 * 覆盖两条链路：定点 / 跟随的 AOE 演出（{@link AoeEffectPacket}）与有朝向的战技演出
 * （{@link CarianStyleCombatArtEffects}）。
 * </p>
 *
 * <h3>设计约束：方法名必须承载语义</h3>
 * <p>
 * 本类的前身是 {@code CarianStyleBurstParticles}，其方法签名带有
 * {@code ParticleOptions particle}、{@code int count}、{@code double spread} 等参数——
 * 那是特效真的会发射原版粒子的年代。后来全部特效改为<b>纯自绘发包</b>，
 * 那个 {@code ParticleOptions} 参数就退化成了「选哪套演出」的<b>隐晦标识符</b>，
 * 其余参数则完全被忽略：
 * </p>
 * <pre>
 * // 旧写法：必须记住「SNOWFLAKE 代表冰爆」这条不成文的约定
 * shockwaveRing(level, x, y, z, radius, 24, ParticleTypes.SNOWFLAKE);
 *
 * // 本类：方法名即语义
 * CarianStyleEffects.frostQuake(level, victim, radius);
 * </pre>
 * <p>
 * 那条约定造成过一次真实事故：冻结地震误以为「粒子环」和「自绘几何」是两套独立视觉，
 * 于是在 {@code shockwaveRing} 之外<b>又手动发了一遍</b>同类型的
 * {@code TYPE_FROST_QUAKE} 包，导致冰爆重叠播放两份（顶点翻倍、亮度偏浓、
 * 多占一个存活特效名额）。
 * </p>
 * <p>
 * 因此本类的硬性约束是：<b>调用点不得依赖任何需要背诵的对应关系</b>。
 * 新增演出时也请遵守——方法名说清它画什么，而不是靠参数值去选。
 * </p>
 *
 * <h3>两条使用规则</h3>
 * <ol>
 *     <li><b>只在服务端调用</b>。调用方自行用 {@code instanceof ServerLevel} 守卫，
 *         本类不重复判断（保持与既有代码风格一致）。</li>
 *     <li><b>特效不产生任何机制影响</b>：不生成实体、不触发事件、不造成伤害，
 *         只向附近客户端广播一个轻量包（约 30 字节），
 *         对服务端 tick 的开销可视为零。因此在任何伤害 / 死亡触发点插入特效都是安全的。</li>
 * </ol>
 *
 * <h3>定点还是跟随？</h3>
 * <p>
 * 这是新增演出时唯一需要认真想一想的选择，判据只有一条：
 * <b>这个演出是「一瞬间的事」还是「持续几秒的状态」？</b>
 * </p>
 * <ul>
 *     <li><b>瞬时反馈用定点</b>——因果律、冻结地震、排斥、龙雷、神圣净化。
 *         这类演出表达「就在这个位置发生了什么」，锁死坐标才有打击感；
 *         跟随反而会让爆闪跟着被击退的目标飘走，把「砍实了」的手感冲淡；</li>
 *     <li><b>持续状态用跟随</b>——猩红立体花、癫火扩散、满月月华。
 *         这类演出要笼罩持有者数秒，期间人会移动 / 被击退 / 主动跑位，
 *         定点会导致「人跑出了自己的光柱」。跟随由客户端每帧取实体插值位置，
 *         实体中途死亡 / 卸载则自动回退到最后已知坐标播完剩余演出。</li>
 * </ul>
 *
 * <h3>如何新增一种全新演出</h3>
 * <p>
 * 若现有演出都不合适，需要新增一套（例如「金色爆裂」），按顺序改这 4 处，
 * 最后在本类补一个语义化方法即可：
 * </p>
 * <ol>
 *     <li>{@link AoeEffectPacket} —— 追加 {@code TYPE_XXX} 常量（<b>只能追加在末尾</b>，
 *         数值不可插队，否则新旧端包 ID 错位）；</li>
 *     <li>{@code AoeEffectManager.durationFor} —— 指定播放时长；
 *         若需要「蓄能对齐延迟触发」再补 {@code chargeUpMsFor / bloomPointFor}；
 *         若需要整体放大再补 {@code scaleFor}；</li>
 *     <li>{@code AoeEffectRenderer.dispatch} —— 追加 case，并新增对应的 {@code drawXxx} 方法；</li>
 *     <li>本类 —— 追加一个语义化静态方法转发到 {@link #send}。</li>
 * </ol>
 * <p>
 * <b>⚠ 第 3 步漏了不会崩溃，但会静默走 {@code default} 分支画成通用蓝白双环</b>
 * （{@link #generic}），因此改完务必进游戏确认一次实际效果。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
public final class CarianStyleEffects {

    // ==================== 广播与默认尺寸常量 ====================

    /**
     * 特效广播范围（格）：只有该范围内的客户端会收到自绘特效包。
     * <p>取 64 是因为它已大于本模组全部 AOE 演出的最大视觉半径
     * （最大者为猩红立体花：基础 5.0 × 放大 1.5 = 7.5，其爆发冲击环再 ×1.9 ≈ 14 格），
     * 站在效果边缘的玩家也能看到完整演出。</p>
     */
    public static final double BROADCAST_RANGE = 64.0;

    /** 通用回退演出的默认半径（格） */
    public static final float GENERIC_RADIUS = 3.0f;

    /** 猩红立体花默认半径（格）。实际渲染时还会被 {@code AoeEffectManager.scaleFor} 放大 1.5 倍 */
    public static final float SCARLET_BLOOM_RADIUS = 5.0f;

    /** 癫火扩散默认半径（格）。同样会被放大 1.5 倍 */
    public static final float FRENZIED_FLAME_RADIUS = 5.0f;

    /** 排斥冲击波默认半径（格） */
    public static final float REPULSION_RADIUS = 2.4f;

    /** 龙雷红色闪电默认半径（格）：仅决定落地冲击环大小，电柱粗细 / 高度由渲染器常量控制 */
    public static final float RED_LIGHTNING_RADIUS = 4.5f;

    /**
     * 满月月华默认半径（格）。
     * <p>决定脚下回春环与月光池的尺寸；头顶月轮与月华柱的高度由渲染器常量控制。
     * 取 3.5 是为了让光池刚好比人形略大一圈，读作「被月光笼罩」而非「站在一个大法阵里」。</p>
     */
    public static final float MOON_BLESSING_RADIUS = 3.5f;

    /**
     * 神圣净化默认半径（格）。
     * <p>决定净化环与地面圣徽的尺寸。取 2.2 是因为这是<b>单体</b>技的命中反馈，
     * 范围做大了会被误读成 AOE、让玩家以为周围的亡灵也吃到了伤害。</p>
     */
    public static final float SACRED_PURGE_RADIUS = 2.2f;

    private CarianStyleEffects() {
    }

    // ============================================================
    //                      因果律金紫法阵
    // ============================================================

    /**
     * 因果律：地面金色六芒星法阵（旋转）+ 内六边形 + 外发光环 + 紫色「因果之线」放射抽射
     * + 六芒星顶点火花。约 1100ms。
     *
     * @param level  服务端世界
     * @param center 中心实体（取其脚底坐标）
     * @param radius 半径（格），建议传入附魔的实际作用半径，做到「看到多大就打多大」
     */
    public static void causality(@Nonnull ServerLevel level, @Nonnull Entity center, float radius) {
        causality(level, center.getX(), center.getY(), center.getZ(), radius);
    }

    /**
     * 因果律（裸坐标版）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（贴地基准高度）
     * @param z      中心 Z
     * @param radius 半径（格）
     */
    public static void causality(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_CAUSALITY);
    }

    // ============================================================
    //                      冻结地震冰爆
    // ============================================================

    /**
     * 冻结地震：12 条放射地裂由内向外生长 + 2 道霜环扩张外滚 + 中心冰花（八角星 + 六边形反向旋转）
     * + 起手中心闪光。约 1000ms。
     *
     * @param level  服务端世界
     * @param center 中心实体（取其脚底坐标）
     * @param radius 半径（格）
     */
    public static void frostQuake(@Nonnull ServerLevel level, @Nonnull Entity center, float radius) {
        frostQuake(level, center.getX(), center.getY(), center.getZ(), radius);
    }

    /**
     * 冻结地震（裸坐标版）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（贴地基准高度）
     * @param z      中心 Z
     * @param radius 半径（格）
     */
    public static void frostQuake(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_FROST_QUAKE);
    }

    // ============================================================
    //                      排斥冲击波
    // ============================================================

    /**
     * 排斥：双发光环从中心猛烈外推扩张并快速淡出（短促的「砰」一下）。约 520ms。
     * <p>使用默认半径 {@link #REPULSION_RADIUS}。</p>
     *
     * @param level  服务端世界
     * @param center 中心实体（取其脚底坐标）
     */
    public static void repulsion(@Nonnull ServerLevel level, @Nonnull Entity center) {
        repulsion(level, center.getX(), center.getY(), center.getZ(), REPULSION_RADIUS);
    }

    /**
     * 排斥（裸坐标 + 默认半径）。
     *
     * @param level 服务端世界
     * @param x     中心 X
     * @param y     中心 Y
     * @param z     中心 Z
     */
    public static void repulsion(@Nonnull ServerLevel level, double x, double y, double z) {
        repulsion(level, x, y, z, REPULSION_RADIUS);
    }

    /**
     * 排斥（裸坐标 + 自定义半径）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     */
    public static void repulsion(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_REPULSION);
    }

    // ============================================================
    //                      龙雷红色闪电
    // ============================================================

    /**
     * 龙雷红色闪电：自天而降的红色之字电柱（白热核 + 红辉光 + 浓红外晕三层）+ 沿途短分叉
     * + 落地红色冲击环与地面强闪。约 1400ms。
     * <p>
     * <b>注意：</b>本演出替代的是原版 {@code LightningBolt}，而原版闪电自带雷声。
     * 去掉原版闪电后<b>需在调用处自行补播</b>
     * {@code SoundEvents.LIGHTNING_BOLT_THUNDER} 与 {@code LIGHTNING_BOLT_IMPACT}，
     * 否则只有画面没有声音（参考 {@code EnchantmentAncientDragonLightning} 的写法）。
     * </p>
     * <p>
     * 客户端 {@code AoeEffectManager} 对本类型做了<b>同位置合并</b>：
     * 2.5 格内的重复落雷只会续命已存在的那道、不新建，
     * 因此古龙雷击那种高频重复降雷不会叠成一团「鬼畜」。
     * </p>
     *
     * @param level 服务端世界
     * @param x     落地点 X
     * @param y     落地点 Y（脚底，电柱由此向上延伸）
     * @param z     落地点 Z
     */
    public static void redLightning(@Nonnull ServerLevel level, double x, double y, double z) {
        redLightning(level, x, y, z, RED_LIGHTNING_RADIUS);
    }

    /**
     * 龙雷红色闪电（自定义落地冲击半径）。
     *
     * @param level  服务端世界
     * @param x      落地点 X
     * @param y      落地点 Y（脚底）
     * @param z      落地点 Z
     * @param radius 落地冲击半径（格）；电柱本身的粗细与高度不受影响
     */
    public static void redLightning(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_RED_LIGHTNING);
    }

    // ============================================================
    //                      满月月华（濒死复活）
    // ============================================================

    /**
     * 满月月华：头顶浮现月轮 → 一道月华柱自上而下笼罩全身 → 脚下每秒一圈<b>向内收拢</b>的
     * 回春环 → 月尘持续上升。约 5000ms，<b>跟随持有者</b>。
     * <p>
     * 用于 {@code EnchantmentFullMoon} 的濒死复活：阻止死亡、残血保命、随后 10 秒
     * （持有暗月时 20 秒）每秒回 0.5% 最大生命。
     * </p>
     * <p>
     * <b>回春环刻意做成向内收拢</b>——本模组其余全部环形演出都是向外扩散（爆发、冲击、排斥），
     * 反向收拢在视觉上直接读作「能量在往身上汇聚」，与「回血」的语义一致，
     * 且同屏叠加时一眼就能和那些爆发类演出区分开。
     * </p>
     * <p>
     * <b>务必用跟随而非定点</b>：复活后玩家往往立刻被继续攻击、被击退或主动跑位，
     * 定点会导致「人跑出了自己的光柱」（详见类注释「定点还是跟随」）。
     * </p>
     * <p>
     * <b>时长不与机制强行对齐。</b>机制回血 10~20 秒，而演出只有 5 秒——
     * 覆盖最戏剧化的前半段即可，全程铺满反而拖沓，且会长时间遮挡玩家自己的视野
     * （复活后正是最需要看清周围的时候）。
     * </p>
     *
     * @param level  服务端世界
     * @param holder 复活的持有者（特效跟随其实时位置）
     */
    public static void moonBlessing(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        moonBlessing(level, holder, holder.getX(), holder.getY(), holder.getZ(), MOON_BLESSING_RADIUS);
    }

    /**
     * 满月月华（自定义坐标与半径）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体（特效跟随其实时位置）；传 {@code null} 则锁定为下方坐标
     * @param x      中心 X（实体消失后的回退坐标）
     * @param y      中心 Y（<b>脚底</b>，月华柱由此向上延伸、回春环贴地）
     * @param z      中心 Z
     * @param radius 半径（格）：决定回春环与月光池尺寸
     */
    public static void moonBlessing(@Nonnull ServerLevel level, @Nullable Entity holder,
                                    double x, double y, double z, float radius) {
        send(level, holder, x, y, z, radius, AoeEffectPacket.TYPE_MOON_BLESSING);
    }

    // ============================================================
    //                    神圣净化（击中亡灵）
    // ============================================================

    /**
     * 神圣净化：目标处金色十字光刃爆开 → 净化环向外扩散 → 金色光尘升天 → 地面圣徽余辉。
     * 约 700ms，<b>定点</b>。
     * <p>
     * 用于 {@code EnchantmentSacredBlade} 击中亡灵：额外伤害 + 吸血 + 永久削弱目标。
     * </p>
     * <p>
     * <b>只在命中亡灵时调用。</b>神圣刀刃对非亡灵是 -80% 伤害的巨大负收益，
     * 那种情况下放净化特效会误导玩家以为打出了强力一击。
     * </p>
     * <p>
     * <b>用定点而非跟随</b>：这是「打中那一下」的瞬时反馈（仅 700ms），
     * 锁在命中坐标才有打击感；跟随会让爆闪跟着被击退的目标飘走
     * （详见类注释「定点还是跟随」）。
     * </p>
     * <p>
     * <b>发包频率提示：</b>本演出的触发点位于每次命中都会走的伤害回调中，
     * 刷亡灵怪塔时会比较密集。客户端侧由 {@code AoeEffectManager.MAX_ACTIVE}(40)
     * 的存活上限兜底，超出时丢弃最早的那个，不会无限堆积。
     * </p>
     *
     * @param level  服务端世界
     * @param victim 被净化的亡灵（取其脚底坐标作为爆闪中心）
     */
    public static void sacredPurge(@Nonnull ServerLevel level, @Nonnull LivingEntity victim) {
        sacredPurge(level, victim.getX(), victim.getY(), victim.getZ(), SACRED_PURGE_RADIUS);
    }

    /**
     * 神圣净化（裸坐标 + 自定义半径）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param radius 半径（格）：决定净化环与地面圣徽尺寸
     */
    public static void sacredPurge(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_SACRED_PURGE);
    }

    // ============================================================
    //              猩红立体花 / 癫火扩散（死亡高潮演出）
    // ============================================================

    /**
     * 猩红艾奥尼亚立体花：竖直 3D 绽放花（四层曼陀罗式花瓣 + 花蕊 + 白热花心）
     * + 地面法阵 + 爆发冲击环 + 凋谢余波。总时长约 5400ms。
     * <p>
     * <b>时间轴已对齐附魔机制：</b>前 1500ms（30 tick）为「花苞缓慢绽放」的蓄能段，
     * 恰好覆盖猩红罗妮亚拉取无敌的前摇；1500ms 处盛放爆发，与第二阶段伤害同步。
     * 因此<b>务必在附魔触发的第一时间调用</b>（而非延迟任务里），否则时序会错位。
     * </p>
     * <p>
     * <b>传入 {@code holder} 会让特效跟随该实体的实时位置</b>——受致命伤后被击退、
     * 视角移动时花都贴身不脱离；实体死亡 / 卸载后客户端自动回退到最后已知坐标继续播完凋谢。
     * </p>
     *
     * @param level  服务端世界
     * @param holder 绑定实体（特效跟随其实时位置）；传 {@code null} 则锁定为下方坐标
     * @param x      中心 X（实体消失后的回退坐标）
     * @param y      中心 Y（<b>脚底</b>，花从地面长起）
     * @param z      中心 Z
     */
    public static void scarletBloom(@Nonnull ServerLevel level, @Nullable Entity holder,
                                    double x, double y, double z) {
        scarletBloom(level, holder, x, y, z, SCARLET_BLOOM_RADIUS);
    }

    /**
     * 猩红艾奥尼亚立体花（自定义半径）。
     * <p>若希望花的大小随附魔等级变化，传入「等级 × N」即可。</p>
     *
     * @param level  服务端世界
     * @param holder 绑定实体；可为 {@code null}
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param radius 半径（格）；客户端还会再乘 1.5 的死亡演出放大系数
     */
    public static void scarletBloom(@Nonnull ServerLevel level, @Nullable Entity holder,
                                    double x, double y, double z, float radius) {
        send(level, holder, x, y, z, radius, AoeEffectPacket.TYPE_SCARLET_BLOOM);
    }

    /**
     * 猩红艾奥尼亚立体花（便捷版：直接绑定实体并取其脚底坐标）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体
     */
    public static void scarletBloom(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        scarletBloom(level, holder, holder.getX(), holder.getY(), holder.getZ(), SCARLET_BLOOM_RADIUS);
    }

    /**
     * 癫火扩散：狂乱蓄能（颤动放射焰舌 + 多重星形法阵）→ 白热爆发冲击环 → 焦黑余烬长尾。
     * 总时长约 5400ms，时间轴对齐规则与 {@link #scarletBloom} 完全相同（1500ms 处爆发）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体（特效跟随其实时位置）；传 {@code null} 则锁定坐标
     * @param x      中心 X
     * @param y      中心 Y（<b>脚底</b>，狂乱裂纹贴地铺开）
     * @param z      中心 Z
     */
    public static void frenziedFlame(@Nonnull ServerLevel level, @Nullable Entity holder,
                                     double x, double y, double z) {
        frenziedFlame(level, holder, x, y, z, FRENZIED_FLAME_RADIUS);
    }

    /**
     * 癫火扩散（自定义半径）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体；可为 {@code null}
     * @param x      中心 X
     * @param y      中心 Y（脚底）
     * @param z      中心 Z
     * @param radius 半径（格）；客户端还会再乘 1.5 的死亡演出放大系数
     */
    public static void frenziedFlame(@Nonnull ServerLevel level, @Nullable Entity holder,
                                     double x, double y, double z, float radius) {
        send(level, holder, x, y, z, radius, AoeEffectPacket.TYPE_FRENZIED_FLAME);
    }

    /**
     * 癫火扩散（便捷版：直接绑定实体并取其脚底坐标）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体
     */
    public static void frenziedFlame(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        frenziedFlame(level, holder, holder.getX(), holder.getY(), holder.getZ(), FRENZIED_FLAME_RADIUS);
    }

    // ============================================================
    //                      通用回退演出
    // ============================================================

    /**
     * 通用回退：中性蓝白双环扩张。
     * <p>没有合适的专属演出、又想先有个视觉占位时用；正式效果建议新增专属类型
     * （步骤见类注释「如何新增一种全新演出」）。</p>
     *
     * @param level  服务端世界
     * @param center 中心实体
     * @param radius 半径（格）
     */
    public static void generic(@Nonnull ServerLevel level, @Nonnull Entity center, float radius) {
        send(level, null, center.getX(), center.getY(), center.getZ(), radius, AoeEffectPacket.TYPE_GENERIC);
    }

    /**
     * 通用回退（裸坐标版）。
     *
     * @param level  服务端世界
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     */
    public static void generic(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_GENERIC);
    }

    // ============================================================
    //                   战技演出（有朝向）
    // ============================================================

    /**
     * 居合斩：沿持有者正面扫出的一道极快水平弧形刀光（银白刀锋 + 残影 + 地面切割线）。约 650ms。
     * <p>转发到 {@link CarianStyleCombatArtEffects}，在此列出只是为了让门面「一处查全」。</p>
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向）
     */
    public static void iaiSlash(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        CarianStyleCombatArtEffects.iaiSlash(level, holder);
    }

    /**
     * 箭步回旋斩：绕自身完整扫过 360° 的环形刀光（银白刀锋 + 琥珀扬尘环）。约 750ms。
     * <p>默认半径与附魔实际 AOE 半径一致（3 格），做到「扫到哪里就打到哪里」。
     * 若你调整了附魔的 AOE 半径，请改用带 radius 的
     * {@link CarianStyleCombatArtEffects#spinSlash(ServerLevel, double, double, double, float, float)}
     * 同步传入新值，否则视觉会骗人。</p>
     *
     * @param level  服务端世界
     * @param holder 持有者
     */
    public static void spinSlash(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        CarianStyleCombatArtEffects.spinSlash(level, holder);
    }

    /**
     * 祈祷一击：自天而降的金色圣光柱 + 落地扩散金环 + 地面十字圣徽 + 升腾金光丝。约 950ms。
     *
     * @param level  服务端世界
     * @param holder 持有者（光降在祈祷者自己身上，而非目标身上）
     */
    public static void prayerStrike(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        CarianStyleCombatArtEffects.prayerStrike(level, holder);
    }

    // ============================================================
    //                          发包底层
    // ============================================================

    /**
     * 向触发点附近广播一个 AOE 自绘特效包（全模组 AOE 特效的唯一出口）。
     *
     * @param level  服务端世界
     * @param holder 绑定实体；{@code null} 表示定点不跟随
     * @param x      中心 X
     * @param y      中心 Y
     * @param z      中心 Z
     * @param radius 半径（格）
     * @param type   特效类型（见 {@link AoeEffectPacket} 的 {@code TYPE_*} 常量）
     */
    public static void send(@Nonnull ServerLevel level, @Nullable Entity holder,
                            double x, double y, double z, float radius, int type) {
        int entityId = (holder == null) ? AoeEffectPacket.NO_ENTITY : holder.getId();
        VisualNetwork.sendToNearby(level, x, y, z, BROADCAST_RANGE,
                new AoeEffectPacket(type, x, y, z, radius, entityId));
    }
}
