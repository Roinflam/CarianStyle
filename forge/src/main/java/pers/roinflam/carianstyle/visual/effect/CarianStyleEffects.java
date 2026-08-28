package pers.roinflam.carianstyle.visual.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.network.VisualNetwork;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
 * 那个 {@code ParticleOptions} 参数就退化成了「选哪套演出」的<b>隐晦标识符</b>：
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
 * {@code TYPE_FROST_QUAKE} 包，导致冰爆重叠播放两份。
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
 *         只向附近客户端广播一个轻量包（约 34 字节），
 *         对服务端 tick 的开销可视为零。因此在任何伤害 / 死亡触发点插入特效都是安全的。</li>
 * </ol>
 *
 * <h3>定点还是跟随？</h3>
 * <p>
 * 判据只有一条：<b>这个演出是「一瞬间的事」还是「持续几秒的状态」？</b>
 * </p>
 * <ul>
 *     <li><b>瞬时反馈用定点</b>——因果律、冻结地震、排斥、龙雷、神圣净化。
 *         这类演出表达「就在这个位置发生了什么」，锁死坐标才有打击感；
 *         跟随反而会让爆闪跟着被击退的目标飘走；</li>
 *     <li><b>持续状态用跟随</b>——猩红立体花、癫火扩散、满月月华。
 *         这类演出要笼罩持有者数秒，期间人会移动 / 被击退 / 主动跑位，
 *         定点会导致「人跑出了自己的光柱」。</li>
 * </ul>
 *
 * <h3>时长：默认按类型，可由服务端指定</h3>
 * <p>
 * 绝大多数演出的机制时长是固定的，客户端按类型查
 * {@code AoeEffectManager.durationFor} 即可，调用方不用操心。
 * </p>
 * <p>
 * 但<b>机制时长本身可变</b>的演出必须由服务端把实际时长发下来。
 * 目前唯一这样的是{@link #moonBlessing 满月月华}——回血持续时间取决于持有者
 * 有没有装备暗月（10 秒或 20 秒），客户端无从得知。
 * 早期版本取最长的 20 秒写死，结果不带暗月时特效比回血多播 10 秒、
 * 玩家早满血了月光还挂在头顶。
 * </p>
 * <p>
 * 新增演出时若遇到同类情况，用 {@link #send} 的 {@code durationMs} 参数传入实际时长；
 * 否则传 {@link AoeEffectPacket#AUTO_DURATION}。
 * </p>
 *
 * <h3>v3.2：龙雷的两级节流（视觉按实体、雷声按网格）</h3>
 *
 * <h4>为什么要分成两级——它们的瓶颈根本不在一处</h4>
 * <p>
 * 两个龙雷附魔的调用模式完全不同，把它们塞进同一套节流里必然顾此失彼：
 * </p>
 * <ul>
 *     <li><b>古龙雷击</b>——死亡时对至多 {@code MAX_TARGETS}(100) 个目标各起一个
 *         {@code SynchronizationTask(40, 5)}，也就是<b>同一目标固定 5 tick 一次</b>、
 *         但<b>同时有上百个目标</b>。瓶颈是<b>横向</b>的：100 目标 × 每 5 tick × 每次 3 个包；</li>
 *     <li><b>维克的龙雷</b>——每次造成伤害按概率触发（雷暴天 100%），只打<b>一个</b>目标，
 *         但高攻速下同一目标一两 tick 内就能连发好几次。瓶颈是<b>纵向</b>的。</li>
 * </ul>
 * <p>
 * 于是：
 * </p>
 * <ul>
 *     <li><b>按实体节流</b>（{@link #RED_LIGHTNING_THROTTLE_TICKS}）只对<b>纵向</b>有效，
 *         也就是只对维克的龙雷有效。对古龙雷击近乎 no-op——它本来就是 5 tick 一次，
 *         节流间隔只要不大于 5 就一次都拦不住。<b>这是刻意的</b>：
 *         古龙雷击每个目标身上那道雷是打击反馈，砍掉一半会让雷变稀疏、机制表现失真；</li>
 *     <li><b>雷声按网格全局节流</b>（{@link #RED_LIGHTNING_SOUND_THROTTLE_TICKS}）才是
 *         古龙雷击真正该省的那块。原实现每次落雷播两个音效
 *         （{@code LIGHTNING_BOLT_THUNDER} + {@code LIGHTNING_BOLT_IMPACT}），
 *         100 目标 × 每 5 tick × 2 ≈ <b>每秒 400 个音效包</b>，
 *         而 20 道雷声同时播<b>只是把音量叠满</b>——玩家听感与播 2 组毫无区别，
 *         MC 的音效系统本身也有同时播放上限，多出来的纯属噪音加带宽。</li>
 * </ul>
 *
 * <h4>雷声为什么按「网格」而不是按实体</h4>
 * <p>
 * 雷声是<b>环境音</b>，玩家听到的是「一片雷在响」，根本分不出是哪个目标身上劈的。
 * 按实体节流等于承认「每个目标都该有自己的一声雷」，那正是要消灭的东西。
 * </p>
 * <p>
 * 但也不能做成纯全局单值——两个玩家在世界两端各自触发时会互相抢配额，
 * 一边有雷声另一边没有。故按 <b>{@value #SOUND_GRID_SIZE} 格见方的网格 + 维度</b>
 * 分桶：同一场战斗的落点天然落在同一个桶里（古龙雷击的搜索半径才 60 格），
 * 不同战场互不干扰。
 * </p>
 *
 * <h4>推荐用法：{@link #redLightningStrike} 一次调用出一次完整雷击</h4>
 * <p>
 * 视觉与音效被合并进同一个入口，调用点只写一行；两级节流在方法内部各自独立生效，
 * <b>互不影响</b>——视觉被拦时雷声仍可能播（反之亦然），这正是想要的：
 * 密集落雷时你会看到每个目标身上都有雷，但只听到稀疏而有力的几声。
 * </p>
 * <p>
 * <b>裸坐标重载保留不动</b>，供确实需要「同一处连续劈」的场景（如固定坐标的仪式类演出）使用。
 * </p>
 *
 * @author FlameForge
 * @version 3.2
 */
public final class CarianStyleEffects {

    // ==================== 广播与默认尺寸常量 ====================

    /**
     * 特效广播范围（格）：只有该范围内的客户端会收到自绘特效包。
     * <p>取 64 是因为它已大于本模组全部 AOE 演出的最大视觉半径
     * （最大者为猩红立体花：基础 5.0 × 放大 1.5 = 7.5，其爆发冲击环再 ×1.9 ≈ 14 格）。</p>
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

    /** 龙雷红色闪电默认半径（格）：仅决定落地冲击环大小 */
    public static final float RED_LIGHTNING_RADIUS = 4.5f;

    /**
     * 满月月华默认半径（格）。
     * <p>决定脚下回春环与月光池的尺寸；头顶月轮与月华柱的高度由渲染器常量控制。
     * 取 3.5 是为了让光池刚好比人形略大一圈，读作「被月光笼罩」而非「站在一个大法阵里」。</p>
     */
    public static final float MOON_BLESSING_RADIUS = 3.5f;

    /**
     * 神圣净化默认半径（格）。
     * <p>取 2.2 是因为这是<b>单体</b>技的命中反馈，范围做大了会被误读成 AOE。</p>
     */
    public static final float SACRED_PURGE_RADIUS = 2.2f;

    /** 每游戏刻的毫秒数（把附魔的 tick 时长换算成特效毫秒时长时用） */
    public static final int MILLIS_PER_TICK = 50;

    // ==================== 龙雷：视觉节流（按实体） ====================

    /**
     * 同一生物身上两道红闪<b>视觉</b>之间的最小间隔（游戏刻）。
     * <p>
     * <b>v3.2：由 5 下调至 {@value}。</b>原值 5 与古龙雷击
     * {@code SynchronizationTask(40, 5)} 的周期<b>恰好相等</b>，
     * 判定 {@code 5 - 5 = 0 < 5} 为 false、每次都通过——功能上没问题，
     * 但整个节流卡在边界上：任何一点 tick 抖动、或将来有人改了那个周期常量，
     * 都会让落雷莫名其妙地被砍掉一半。留出余量更稳。
     * </p>
     * <p>
     * <b>刻意不再调大：</b>古龙雷击每个目标身上那道雷是打击反馈，
     * 节流间隔一旦超过 5 就会开始砍掉真实落雷，雷变稀疏、机制表现失真。
     * 本级节流的定位只是挡住<b>维克的龙雷</b>在高攻速下的同目标连发
     * （详见类注释「为什么要分成两级」）。
     * </p>
     */
    public static final int RED_LIGHTNING_THROTTLE_TICKS = 3;

    /**
     * 红闪<b>视觉</b>节流表：目标实体网络 id -&gt; 上次发包的游戏刻。
     * <p>
     * 仅服务端主线程访问（附魔的伤害 / 死亡事件都在主线程），
     * 此处仍用 {@link ConcurrentHashMap} 是出于对 Mohist 等混合端可能的跨线程调用的保守考虑，
     * 与 {@code MobEffectScarletRot} 的缓存策略一致。
     * </p>
     */
    private static final Map<Integer, Long> RED_LIGHTNING_LAST_TICK = new ConcurrentHashMap<>();

    // ==================== 龙雷：雷声节流（按网格） ====================

    /**
     * 同一区域内两组<b>雷声</b>之间的最小间隔（游戏刻）。
     * <p>
     * 取 {@value}（0.15 秒）的依据：古龙雷击是「每 5 tick 一波、一波打多个目标」，
     * 本值使<b>每一波至多播一组雷声</b>，而不是每个目标各播一组。
     * 100 目标场景下音效包量从约 400/秒降到约 40/秒，<b>降幅一个数量级</b>，
     * 而听感几乎不变——20 道雷同时响本来就只是把音量叠满。
     * </p>
     * <p>
     * 调小会让雷声更密（更吵、更费包），调大会让密集落雷显得没声音。
     * </p>
     */
    public static final int RED_LIGHTNING_SOUND_THROTTLE_TICKS = 3;

    /**
     * 雷声节流的网格边长（格）。
     * <p>
     * 取 {@value} 的依据：古龙雷击的搜索半径是 60 格，
     * 同一次触发的全部落点必定落在相邻一两个网格内，因此同场战斗会共用配额；
     * 而世界两端的两场战斗必定分属不同网格，不会互相抢（详见类注释）。
     * </p>
     */
    private static final int SOUND_GRID_SIZE = 64;

    /**
     * {@link #SOUND_GRID_SIZE} 对应的右移位数（{@code 64 = 1 << 6}）。
     * <p>用位移而非除法：坐标可能为负，整数除法向零取整会让 -1 和 +1 落进同一格，
     * 而算术右移向下取整、负坐标的分桶才是均匀的。</p>
     */
    private static final int SOUND_GRID_SHIFT = 6;

    /**
     * 红闪<b>雷声</b>节流表：网格键 -&gt; 上次播放的游戏刻。
     * <p>键由 {@link #soundGridKey} 混合维度与网格坐标生成。</p>
     */
    private static final Map<Long, Long> RED_LIGHTNING_SOUND_LAST_TICK = new ConcurrentHashMap<>();

    // ===== 雷声参数（原先散落在两个附魔类里，现集中于此以保证两处听感一致）=====

    /** 雷鸣音量 */
    private static final float THUNDER_VOLUME = 0.8f;
    /** 雷鸣基准音高 */
    private static final float THUNDER_PITCH_BASE = 0.9f;
    /** 雷鸣音高随机范围（避免高频重复时听感机械） */
    private static final float THUNDER_PITCH_RANGE = 0.2f;
    /** 落地冲击音量 */
    private static final float IMPACT_VOLUME = 0.5f;
    /** 落地冲击基准音高 */
    private static final float IMPACT_PITCH_BASE = 1.0f;
    /** 落地冲击音高随机范围 */
    private static final float IMPACT_PITCH_RANGE = 0.2f;

    // ==================== 节流表的惰性清理（两张表共用） ====================

    /**
     * 节流表条目数超过此值时，在下一次写入时顺手清理过期项。
     * <p>
     * 取 128 的依据：古龙雷击单次触发最多命中 {@code MAX_TARGETS}(100) 个目标，
     * 因此稳态下条目数不会长期超过这个量级；真超过了也只是多做一次遍历清理，成本可忽略。
     * 与 {@code EnchantmentDataManager} 的惰性清理策略一致——不额外注册 tick 事件，
     * 把清理成本摊薄在写入路径上、且自我限制，不会无限增长。
     * </p>
     */
    private static final int THROTTLE_SWEEP_THRESHOLD = 128;

    /**
     * 过期判定的宽限倍数：条目最后一次使用距今超过
     * {@code 节流间隔 × 本值} tick 即视为可清理。
     * <p>取 8 是留出充裕余量，避免把「刚好在两次落雷之间」的活跃条目误清掉——
     * 误清的后果只是多发一个包，不严重，但没必要。</p>
     */
    private static final int THROTTLE_EXPIRY_MULTIPLIER = 8;

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
     * 冻结地震：12 条放射地裂由内向外生长 + 2 道霜环扩张外滚 + 中心冰花 + 起手中心闪光。约 1000ms。
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
     * 龙雷完整雷击（<b>推荐入口</b>）：红色电柱视觉 + 雷鸣 / 落地音效，两者各自独立节流。
     * <p>
     * 这是给两个龙雷附魔用的一站式方法，调用点只需一行。内部做两件事：
     * </p>
     * <ol>
     *     <li>视觉——按<b>目标实体</b>节流（{@value #RED_LIGHTNING_THROTTLE_TICKS} tick）；</li>
     *     <li>雷声——按<b>{@value #SOUND_GRID_SIZE} 格网格</b>节流
     *         （{@value #RED_LIGHTNING_SOUND_THROTTLE_TICKS} tick）。</li>
     * </ol>
     * <p>
     * <b>两级节流互不影响。</b>视觉被拦时雷声仍可能播，反之亦然——这正是想要的效果：
     * 密集落雷时每个目标身上都看得见雷，但只听到稀疏而有力的几声，
     * 而不是二十道雷声糊成一团白噪音。
     * </p>
     * <p>
     * <b>替代了什么：</b>原版蓝白 {@code LightningBolt}（{@code setVisualOnly(true)}）
     * 本就只有「视觉 + 音效」无副作用。换成自绘红闪后音效随它一起没了，
     * 因此雷声必须自己补——现在补在本方法内，两个附魔不必再各自维护一份
     * {@code playSound}（原先那两份的音量 / 音高完全相同，属于复制粘贴，
     * 改一处忘一处就会导致两个附魔雷声不一样）。
     * </p>
     *
     * @param level  服务端世界
     * @param target 被劈的目标（取其脚底坐标；同时作为视觉节流的键）
     */
    public static void redLightningStrike(@Nonnull ServerLevel level, @Nonnull Entity target) {
        redLightningStrike(level, target, RED_LIGHTNING_RADIUS);
    }

    /**
     * 龙雷完整雷击（自定义落地冲击半径）。
     *
     * @param level  服务端世界
     * @param target 被劈的目标
     * @param radius 落地冲击半径（格）；电柱本身的粗细与高度不受影响
     */
    public static void redLightningStrike(@Nonnull ServerLevel level, @Nonnull Entity target, float radius) {
        double x = target.getX();
        double y = target.getY();
        double z = target.getZ();
        long gameTime = level.getGameTime();

        if (shouldEmitRedLightning(target.getId(), gameTime)) {
            redLightning(level, x, y, z, radius);
        }
        if (shouldEmitRedLightningSound(level, x, z, gameTime)) {
            playRedLightningSound(level, x, y, z);
        }
    }

    /**
     * 龙雷红色闪电<b>视觉</b>（带按实体节流）：自天而降的红色之字电柱 + 沿途短分叉
     * + 落地红色冲击环与地面强闪。约 1400ms。
     * <p>
     * 同一个目标身上 {@value #RED_LIGHTNING_THROTTLE_TICKS} tick 内的重复调用会被<b>直接丢弃</b>
     * （连包都不发）。<b>不含音效</b>——需要完整雷击请用 {@link #redLightningStrike}。
     * </p>
     *
     * @param level  服务端世界
     * @param target 被劈的目标（取其脚底坐标；同时作为节流的键）
     */
    public static void redLightning(@Nonnull ServerLevel level, @Nonnull Entity target) {
        if (!shouldEmitRedLightning(target.getId(), level.getGameTime())) {
            return;
        }
        redLightning(level, target.getX(), target.getY(), target.getZ(), RED_LIGHTNING_RADIUS);
    }

    /**
     * 龙雷红色闪电视觉（带按实体节流 + 自定义落地冲击半径）。
     *
     * @param level  服务端世界
     * @param target 被劈的目标（同时作为节流的键）
     * @param radius 落地冲击半径（格）
     */
    public static void redLightning(@Nonnull ServerLevel level, @Nonnull Entity target, float radius) {
        if (!shouldEmitRedLightning(target.getId(), level.getGameTime())) {
            return;
        }
        redLightning(level, target.getX(), target.getY(), target.getZ(), radius);
    }

    /**
     * 龙雷红色闪电视觉（<b>裸坐标版，不节流</b>）。
     * <p>
     * 供确实需要「同一处连续劈」的场景使用（如固定坐标的仪式类演出）。
     * 对着某个生物反复调用请改用 {@link #redLightningStrike}。
     * </p>
     * <p>
     * 客户端 {@code AoeEffectManager} 对本类型仍有三重兜底节流
     * （同位置合并 4.5 格 / 专用上限 12 道 / 新建限速 45ms），
     * 因此即便走本重载狂发包也不会把客户端打崩——只是白费带宽。
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
     * 龙雷红色闪电视觉（裸坐标版，不节流，自定义落地冲击半径）。
     *
     * @param level  服务端世界
     * @param x      落地点 X
     * @param y      落地点 Y（脚底）
     * @param z      落地点 Z
     * @param radius 落地冲击半径（格）
     */
    public static void redLightning(@Nonnull ServerLevel level, double x, double y, double z, float radius) {
        send(level, null, x, y, z, radius, AoeEffectPacket.TYPE_RED_LIGHTNING);
    }

    /**
     * 龙雷<b>雷声</b>（带按网格节流）：雷鸣 + 落地冲击两声。
     * <p>
     * 同一 {@value #SOUND_GRID_SIZE} 格网格内 {@value #RED_LIGHTNING_SOUND_THROTTLE_TICKS} tick
     * 内的重复调用会被丢弃。这一级是<b>古龙雷击真正省下开销的地方</b>：
     * 100 目标场景下音效包量从约 400/秒降到约 40/秒，而听感几乎不变
     * （详见类注释「雷声为什么按网格而不是按实体」）。
     * </p>
     *
     * @param level 服务端世界
     * @param x     落地点 X
     * @param y     落地点 Y
     * @param z     落地点 Z
     */
    public static void redLightningSound(@Nonnull ServerLevel level, double x, double y, double z) {
        if (!shouldEmitRedLightningSound(level, x, z, level.getGameTime())) {
            return;
        }
        playRedLightningSound(level, x, y, z);
    }

    /**
     * 无条件播放一组龙雷音效（雷鸣 + 落地冲击），<b>不做任何节流</b>。
     * <p>
     * 音高带轻微随机，避免高频重复时听感机械。参数集中在本类顶部的
     * {@code THUNDER_*} / {@code IMPACT_*} 常量，两个龙雷附魔共用同一份。
     * </p>
     *
     * @param level 服务端世界
     * @param x     落地点 X
     * @param y     落地点 Y
     * @param z     落地点 Z
     */
    private static void playRedLightningSound(@Nonnull ServerLevel level, double x, double y, double z) {
        level.playSound(null, x, y, z,
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER,
                THUNDER_VOLUME, THUNDER_PITCH_BASE + level.random.nextFloat() * THUNDER_PITCH_RANGE);
        level.playSound(null, x, y, z,
                SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER,
                IMPACT_VOLUME, IMPACT_PITCH_BASE + level.random.nextFloat() * IMPACT_PITCH_RANGE);
    }

    /**
     * 判定某目标本刻是否允许发出红闪<b>视觉</b>包，并在允许时登记时间戳。
     * <p>
     * 供 {@link #redLightningStrike} 与 {@link #redLightning(ServerLevel, Entity)} 内部调用；
     * 也对外公开，以便附魔在「决定要不要连带做别的事」这类场景里自行查询。
     * </p>
     * <p>
     * <b>顺手做惰性清理：</b>条目数超过 {@value #THROTTLE_SWEEP_THRESHOLD} 时清一遍过期项。
     * </p>
     *
     * @param entityId 目标实体网络 id
     * @param gameTime 当前游戏刻（{@code level.getGameTime()}）
     * @return 允许发包返回 true（并已登记时间戳）；被节流返回 false
     */
    public static boolean shouldEmitRedLightning(int entityId, long gameTime) {
        Long last = RED_LIGHTNING_LAST_TICK.get(entityId);
        if (last != null && isWithin(gameTime, last, RED_LIGHTNING_THROTTLE_TICKS)) {
            return false;
        }
        if (RED_LIGHTNING_LAST_TICK.size() > THROTTLE_SWEEP_THRESHOLD) {
            sweep(RED_LIGHTNING_LAST_TICK, gameTime, RED_LIGHTNING_THROTTLE_TICKS);
        }
        RED_LIGHTNING_LAST_TICK.put(entityId, gameTime);
        return true;
    }

    /**
     * 判定某位置本刻是否允许播放龙雷<b>雷声</b>，并在允许时登记时间戳。
     *
     * @param level    服务端世界（取维度参与分桶）
     * @param x        落地点 X
     * @param z        落地点 Z
     * @param gameTime 当前游戏刻
     * @return 允许播放返回 true（并已登记时间戳）；被节流返回 false
     */
    public static boolean shouldEmitRedLightningSound(@Nonnull ServerLevel level,
                                                      double x, double z, long gameTime) {
        long key = soundGridKey(level, x, z);
        Long last = RED_LIGHTNING_SOUND_LAST_TICK.get(key);
        if (last != null && isWithin(gameTime, last, RED_LIGHTNING_SOUND_THROTTLE_TICKS)) {
            return false;
        }
        if (RED_LIGHTNING_SOUND_LAST_TICK.size() > THROTTLE_SWEEP_THRESHOLD) {
            sweep(RED_LIGHTNING_SOUND_LAST_TICK, gameTime, RED_LIGHTNING_SOUND_THROTTLE_TICKS);
        }
        RED_LIGHTNING_SOUND_LAST_TICK.put(key, gameTime);
        return true;
    }

    /**
     * 生成雷声节流用的网格键：维度 + {@value #SOUND_GRID_SIZE} 格见方的水平网格。
     * <p>
     * 用<b>算术右移</b>而非整数除法：坐标可能为负，而整数除法向零取整会让
     * x=-1 与 x=+1 落进同一格（都是 0），负半轴的分桶会比正半轴粗一倍；
     * 算术右移向下取整，正负两侧的分桶才是均匀的。
     * </p>
     * <p>
     * 维度用 {@code dimension().location().hashCode()} 参与混合——
     * 不同维度的同一坐标必须分属不同桶，否则主世界的战斗会吃掉下界的雷声配额。
     * </p>
     *
     * @param level 服务端世界
     * @param x     世界坐标 X
     * @param z     世界坐标 Z
     * @return 网格键
     */
    private static long soundGridKey(@Nonnull ServerLevel level, double x, double z) {
        long gx = ((long) Math.floor(x)) >> SOUND_GRID_SHIFT;
        long gz = ((long) Math.floor(z)) >> SOUND_GRID_SHIFT;
        long dim = level.dimension().location().hashCode() & 0xFFFFFFFFL;
        long key = gx * 0x9E3779B97F4A7C15L;
        key ^= gz * 0xC2B2AE3D27D4EB4FL;
        key ^= dim * 0x165667B19E3779F9L;
        return key;
    }

    /**
     * 判定「距上次不足间隔」。
     * <p>
     * 单独提出来是为了统一处理一个边界：{@code gameTime} 在世界重载后可能<b>变小</b>
     * （换存档 / 回退 / {@code /time set}），此时 {@code gameTime - last} 为负、
     * 恒小于任何正阈值，会把节流永久卡死。故负差值一律视为「不在间隔内」（放行）。
     * </p>
     *
     * @param gameTime 当前游戏刻
     * @param last     上次记录的游戏刻
     * @param ticks    节流间隔
     * @return 仍在节流间隔内返回 true
     */
    private static boolean isWithin(long gameTime, long last, int ticks) {
        long diff = gameTime - last;
        return diff >= 0L && diff < ticks;
    }

    /**
     * 清理节流表中的过期条目（视觉表与雷声表共用）。
     * <p>
     * 「过期」定义为最后一次使用距今超过
     * {@code 节流间隔 × }{@value #THROTTLE_EXPIRY_MULTIPLIER} tick。
     * 留这么大的宽限是为了不误清活跃条目——误清的后果只是多发一个包，不严重，但没必要。
     * </p>
     * <p>
     * 同时兜住 {@code gameTime} 变小的情形（见 {@link #isWithin}）：负差值同样判为过期、直接清掉，
     * 否则旧条目会永远留在表里。
     * </p>
     *
     * @param table    待清理的节流表
     * @param gameTime 当前游戏刻
     * @param ticks    该表的节流间隔
     * @param <K>      键类型
     */
    private static <K> void sweep(@Nonnull Map<K, Long> table, long gameTime, int ticks) {
        long expiry = (long) ticks * THROTTLE_EXPIRY_MULTIPLIER;
        Iterator<Map.Entry<K, Long>> it = table.entrySet().iterator();
        while (it.hasNext()) {
            long diff = gameTime - it.next().getValue();
            if (diff < 0L || diff > expiry) {
                it.remove();
            }
        }
    }

    /**
     * 清空龙雷的两张节流表（服务器停止 / 换世界等场景可调用，非必需）。
     * <p>不调用也无妨：条目会由 {@link #sweep} 惰性清掉，
     * 且表的上限就是同屏实体 / 活跃网格的数量级，不存在内存风险。</p>
     */
    public static void clearRedLightningThrottle() {
        RED_LIGHTNING_LAST_TICK.clear();
        RED_LIGHTNING_SOUND_LAST_TICK.clear();
    }

    // ============================================================
    //                      满月月华（濒死复活）
    // ============================================================

    /**
     * 满月月华：头顶浮现<b>球形月轮</b> → 月华柱自上而下笼罩全身 →
     * 脚下每秒一圈<b>向内收拢</b>的回春环 → 月尘上升。<b>跟随持有者</b>。
     * <p>
     * 用于 {@code EnchantmentFullMoon} 的濒死复活：阻止死亡、残血保命、随后持续回血。
     * </p>
     *
     * <h4>⚠ 必须传入实际回血时长</h4>
     * <p>
     * 满月的回血持续时间<b>取决于持有者有没有装备暗月</b>（200 tick 或 400 tick），
     * 而客户端无从得知这一点。早期版本让客户端取最长的 20 秒写死，
     * 结果不带暗月时特效比实际回血多播 10 秒——玩家早满血了月光还挂在头顶。
     * </p>
     * <p>
     * 因此调用方<b>必须</b>把附魔里算出的那个 {@code duration}（tick）传进来，
     * 由本方法换算成毫秒随包发下。渲染器会用它把归一化进度换算回绝对秒数，
     * 因此 10 秒版与 20 秒版的<b>动画速度完全一致</b>，只是持续段长短不同。
     * </p>
     *
     * @param level         服务端世界
     * @param holder        复活的持有者（特效跟随其实时位置）
     * @param durationTicks 实际回血时长（游戏刻）——直接传附魔里那个 {@code duration} 变量
     */
    public static void moonBlessing(@Nonnull ServerLevel level, @Nonnull LivingEntity holder,
                                    int durationTicks) {
        moonBlessing(level, holder, holder.getX(), holder.getY(), holder.getZ(),
                MOON_BLESSING_RADIUS, durationTicks * MILLIS_PER_TICK);
    }

    /**
     * 满月月华（自定义坐标、半径与时长）。
     *
     * @param level      服务端世界
     * @param holder     绑定实体（特效跟随其实时位置）；传 {@code null} 则锁定为下方坐标
     * @param x          中心 X（实体消失后的回退坐标）
     * @param y          中心 Y（<b>脚底</b>，月华柱由此向上延伸、回春环贴地）
     * @param z          中心 Z
     * @param radius     半径（格）：决定回春环与月光池尺寸
     * @param durationMs 播放时长（毫秒）；传 {@link AoeEffectPacket#AUTO_DURATION} 用客户端默认值
     */
    public static void moonBlessing(@Nonnull ServerLevel level, @Nullable Entity holder,
                                    double x, double y, double z, float radius, int durationMs) {
        send(level, holder, x, y, z, radius, AoeEffectPacket.TYPE_MOON_BLESSING, durationMs);
    }

    // ============================================================
    //                    神圣净化（击中亡灵）
    // ============================================================

    /**
     * 神圣净化：目标处金色三维十字光刃爆开 → 净化环向外扩散 → 金色光尘升天 → 地面圣徽余辉。
     * 约 700ms，<b>定点</b>。
     * <p>
     * 用于 {@code EnchantmentSacredBlade} 击中亡灵。
     * <b>只在命中亡灵时调用</b>——对非亡灵是 -80% 伤害的巨大负收益，
     * 那种情况下放净化特效会误导玩家以为打出了强力一击。
     * </p>
     * <p>
     * <b>用定点而非跟随</b>：这是「打中那一下」的瞬时反馈（仅 700ms），
     * 锁在命中坐标才有打击感。
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
     * 猩红艾奥尼亚立体花：竖直 3D 绽放花 + 地面法阵 + 爆发冲击环 + 凋谢余波。总时长约 5400ms。
     * <p>
     * <b>时间轴已对齐附魔机制：</b>前 1500ms（30 tick）为「花苞缓慢绽放」的蓄能段，
     * 恰好覆盖猩红罗妮亚拉取无敌的前摇；1500ms 处盛放爆发，与第二阶段伤害同步。
     * 因此<b>务必在附魔触发的第一时间调用</b>（而非延迟任务里），否则时序会错位。
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
     * 癫火扩散：狂乱蓄能 → 白热爆发冲击环 → 焦黑余烬长尾。总时长约 5400ms，
     * 时间轴对齐规则与 {@link #scarletBloom} 完全相同（1500ms 处爆发）。
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
     * <p>没有合适的专属演出、又想先有个视觉占位时用；正式效果建议新增专属类型。</p>
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
     * 居合斩：沿持有者正面扫出的一道极快水平弧形刀光。约 650ms。
     * <p>转发到 {@link CarianStyleCombatArtEffects}，在此列出只是为了让门面「一处查全」。</p>
     *
     * @param level  服务端世界
     * @param holder 持有者（自动取其位置与朝向）
     */
    public static void iaiSlash(@Nonnull ServerLevel level, @Nonnull LivingEntity holder) {
        CarianStyleCombatArtEffects.iaiSlash(level, holder);
    }

    /**
     * 箭步回旋斩：绕自身完整扫过 360° 的环形刀光。约 750ms。
     * <p>默认半径与附魔实际 AOE 半径一致（3 格）。若你调整了附魔的 AOE 半径，
     * 请改用带 radius 的重载同步传入新值，否则视觉会骗人。</p>
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
     * 向触发点附近广播一个 AOE 自绘特效包（默认时长）。
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
        send(level, holder, x, y, z, radius, type, AoeEffectPacket.AUTO_DURATION);
    }

    /**
     * 向触发点附近广播一个 AOE 自绘特效包（全模组 AOE 特效的唯一出口）。
     *
     * @param level      服务端世界
     * @param holder     绑定实体；{@code null} 表示定点不跟随
     * @param x          中心 X
     * @param y          中心 Y
     * @param z          中心 Z
     * @param radius     半径（格）
     * @param type       特效类型（见 {@link AoeEffectPacket} 的 {@code TYPE_*} 常量）
     * @param durationMs 播放时长（毫秒）；{@link AoeEffectPacket#AUTO_DURATION} 为按类型取默认值
     */
    public static void send(@Nonnull ServerLevel level, @Nullable Entity holder,
                            double x, double y, double z, float radius, int type, int durationMs) {
        int entityId = (holder == null) ? AoeEffectPacket.NO_ENTITY : holder.getId();
        VisualNetwork.sendToNearby(level, x, y, z, BROADCAST_RANGE,
                new AoeEffectPacket(type, x, y, z, radius, entityId, durationMs));
    }
}
