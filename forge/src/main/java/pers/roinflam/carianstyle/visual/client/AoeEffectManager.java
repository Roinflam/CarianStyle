package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

/**
 * 定点 AOE 自绘特效管理器（纯客户端）。
 * <p>
 * 收到 {@link AoeEffectPacket} 后，{@link #spawn} 创建一个带生命周期的
 * {@link AoeEffect} 并加入存活列表；客户端每 tick 推进、到期销毁；{@link AoeEffectRenderer} 每帧读取
 * 列表自绘。所有访问都在客户端主线程（网络 handle 经 enqueueWork、tick/渲染均主线程），故用普通
 * {@link ArrayList} 即可，无并发问题。
 * </p>
 * <p>
 * <b>说明：</b>特效时长是<b>纯客户端的视觉播放时长</b>，与服务端附魔的实际机制时长（无敌、拉取、伤害）
 * 完全解耦——延长视觉时长不影响任何游戏机制。猩红罗妮亚 / 癫火蔓延为多段演出，时长按
 * 「触发即满状态蓄能（覆盖约 1.5 秒拉取无敌前摇）→ 大爆发 → 长余波」编排，故较长。
 * </p>
 *
 * <h3>v6.1 修复：死亡触发类附魔的特效在玩家复活后继续跟随</h3>
 * <p>
 * <b>现象：</b>猩红艾奥尼亚 / 癫火蔓延（俗称「大灭」）触发后，附魔会在 30tick 处把持有者杀死，
 * 而特效总时长 5400ms，剩余约 3.9 秒的凋谢 / 余烬段本应留在死亡点原地播完。
 * 但玩家一旦复活，特效会<b>瞬间吸附回复活后的玩家身上并跟着跑</b>，直到剩余时长走完。
 * </p>
 * <p>
 * <b>根因：</b>Minecraft 玩家复活时<b>实体网络 ID 不变</b>——服务端
 * {@code PlayerList.respawn} 里会 {@code serverplayer.setId(player.getId())}，
 * 客户端 {@code ClientPacketListener.handleRespawn} 同样把新 {@code LocalPlayer} 的 id
 * 设回旧值。而 {@link AoeEffectRenderer} 每帧都拿 {@code fx.entityId} 去
 * {@code level.getEntity(id)} 重新解析实体，一旦解析到的实体重新 {@code isAlive()}，
 * 就会继续跟随。于是死亡 → 特效脱离 → 复活 → 特效再次吸附，形成残留跟随。
 * </p>
 * <p>
 * <b>修复：</b>{@link AoeEffect#entityId} 去掉 {@code final}，
 * 在 {@link #onClientTick} 中每 tick 调用 {@link #detachDeadBindings}：
 * 绑定实体一旦<b>死亡或卸载</b>，立即把 {@code entityId} 置为 {@link AoeEffectPacket#NO_ENTITY}
 * <b>永久解绑</b>，此后再也不会重新解析实体。渲染器侧的
 * {@code if (fx.entityId >= 0)} 分支自然不成立，直接走「最后已知坐标」路径，
 * <b>渲染器无需任何改动</b>，凋谢 / 余烬段仍在死亡点原地播完，行为与设计一致。
 * </p>
 * <p>
 * <b>为什么解绑是安全的：</b>渲染器在实体存活期间每帧都会把实时插值坐标写回
 * {@code fx.x/y/z}，因此解绑瞬间 {@code fx} 里存的就是最后一帧的准确位置；
 * 而实体临时离开视野（区块卸载）本来就走同一条「最后已知坐标」回退路径，
 * 特效最长仅 20 秒、裁剪距离 96 格，不存在需要「离开后再回来重新吸附」的合理场景。
 * </p>
 *
 * <h3>v6.2 性能：存活上限由 64 下调至 40</h3>
 * <p>
 * <b>为什么原先是 64：</b>该上限设立时，客户端渲染尚无任何细节层级裁剪，
 * 它是防止「特效无限堆积」的<b>唯一</b>兜底，因此取得比较宽松。
 * </p>
 * <p>
 * <b>为什么现在可以下调：</b>{@link AoeEffectRenderer} 已接入 {@link VisualLod}，
 * 远处与同屏拥挤时会按细节系数削减顶点。但 LOD 削的是<b>单个特效的顶点量</b>，
 * 削不掉「同屏 64 个独立特效」本身带来的固定开销——每个特效仍要各自走一遍
 * 距离裁剪、进度映射、类型分发与几何生成的调用链。二者是互补关系，不是替代关系。
 * </p>
 * <p>
 * <b>丢弃策略未变：</b>超出上限时移除<b>最早的</b>（{@code ACTIVE.remove(0)}）。
 * 对红闪而言，最早那道也是最接近消散的，丢弃它的观感损失最小。
 * </p>
 *
 * <h3>v6.3 新增：满月月华（7）与神圣净化（8）</h3>
 * <p>
 * 两者都只需在 {@link #durationFor} 追加时长，<b>不涉及分段进度映射</b>
 * （{@link #chargeUpMsFor} 返回 0，走线性）与<b>尺寸放大</b>（{@link #scaleFor} 返回 1.0）。
 * </p>
 *
 * <h3>v6.4 调整：满月月华 5 秒 → 20 秒，覆盖整个回血期</h3>
 * <p>
 * <b>为什么原先是 5 秒：</b>当初的考虑是「覆盖最戏剧化的前半段即可，
 * 全程铺满反而拖沓、还会长时间挡住玩家自己的视野」。
 * </p>
 * <p>
 * <b>为什么改：</b>实测下来这个判断是错的。满月的机制是复活后持续回血
 * 10 秒（持有暗月时 400 tick = 20 秒），而演出 5 秒就退场——
 * 玩家还在一格一格回血，头顶却已经什么都没有了，
 * 会误以为「效果结束了」。<b>演出的作用之一就是告诉玩家状态还在持续</b>，
 * 提前退场等于丢掉了这个信息。
 * </p>
 * <p>
 * 现取 20000ms 覆盖<b>最长</b>情况（持有暗月）。不带暗月时机制只回 10 秒，
 * 演出会多播 10 秒——但那时玩家已经满血，多余的月光无害。
 * </p>
 * <p>
 * <b>「挡视野」的顾虑改用别的办法解决：</b>
 * {@link AoeEffectRenderer#drawMoonBlessing} 会在第 1.5~3.5 秒把月华柱
 * <b>收细变淡到三成</b>——起手那两秒给足冲击，之后柱子让位，
 * 月轮与回春环则全程保留。该消失的是遮视野的那根柱子，
 * 不是「我在回血」这个信息本身。
 * </p>
 * <p>
 * <b>⚠ 改这个值必须同步改 {@code AoeEffectRenderer.MOON_BLESSING_SECONDS}</b>
 * （那边是秒，即本值 ÷ 1000）。渲染器用它把归一化的 progress 换算回绝对秒数，
 * 从而让动画速度与总时长脱钩——两处不一致会导致月轮转速、回春环频率整体偏快或偏慢。
 * </p>
 *
 * <h3>v6.5 新增：服务端可指定播放时长</h3>
 * <p>
 * {@link #spawn(int, double, double, double, float, int, int)} 新增 {@code durationMs} 参数。
 * 传 {@link AoeEffectPacket#AUTO_DURATION}(-1) 即按类型走 {@link #durationFor}（全部既有演出的行为）；
 * 传具体毫秒数则覆盖之。
 * </p>
 * <p>
 * <b>动机：</b>v6.4 把满月月华写死成 20 秒，但机制回血是 10 秒（持有暗月才 20 秒），
 * 于是不带暗月时特效比回血多播 10 秒——玩家早满血了月光还挂在头顶。
 * 客户端无从判断持有者有没有暗月，只能由服务端把实际时长发下来。
 * </p>
 * <p>
 * <b>渲染器侧同步简化：</b>{@link AoeEffectRenderer} 不再需要维护任何「时长常量」——
 * 它直接从 {@link AoeEffect#birthMs} 与 {@link AoeEffect#durationMs} 算出
 * 绝对播放秒数与剩余秒数，动画速度天然与总时长无关。
 * v6.4 那个「两处常量必须手工同步」的隐患随之消失。
 * </p>
 *
 * <h3>v6.6 性能：红色闪电的三重节流（本次新增）</h3>
 * <p>
 * <b>问题：</b>红闪是全模组唯一能把 {@link #MAX_ACTIVE} 跑满的类型，而且它的爆发时机
 * <b>恰好是客户端最卡的那一瞬</b>——古龙雷击在持有者濒死时对 {@code MAX_TARGETS}(100)
 * 个目标各自降雷，每目标最多 {@code level × 15} 道，与此同时场上正有大量实体死亡。
 * </p>
 * <p>
 * 旧实现只有「同位置合并」一道闸，且半径仅 2.5 格。目标一旦散开就各成一道，
 * 单道红闪开场峰值约 4400 顶点，跑满 40 道即 <b>17.6 万顶点</b>。
 * {@link VisualLod} 削的是单道的顶点量，削不掉「40 道各自走一遍距离裁剪、
 * 进度映射、类型分发、19 段主干节点生成」的固定调用开销——二者是互补关系。
 * </p>
 * <p>
 * <b>本次加了三重闸，全部集中在 {@link #tryAbsorbRedLightning} 一处：</b>
 * </p>
 * <ol>
 *     <li><b>合并半径 2.5 → {@value #RED_LIGHTNING_MERGE_DIST} 格</b>
 *         （{@link #RED_LIGHTNING_MERGE_DIST_SQR}）。原值只覆盖「同一个目标在原地被反复劈」，
 *         放宽后「挤在一堆的几个目标」也会收敛成一道。代价是相邻目标不再各有一道雷——
 *         但 4.5 格内同时存在多道闪电本来就会糊成一片，分开画属于纯浪费；</li>
 *     <li><b>红闪专用数量上限 {@value #MAX_RED_LIGHTNING} 道</b>（{@link #MAX_RED_LIGHTNING}）。
 *         这是与 {@link #MAX_ACTIVE} 独立的一道闸：后者管的是「全部类型加起来」，
 *         而红闪一家就能吃满，导致同时触发的因果律 / 立体花被挤掉。分开限制之后，
 *         红闪最多占 12 席，剩下的位置留给其它演出；</li>
 *     <li><b>新建限速 {@value #RED_LIGHTNING_SPAWN_COOLDOWN_MS} ms</b>
 *         （{@link #RED_LIGHTNING_SPAWN_COOLDOWN_MS}）。前两道闸都是「空间」维度的，
 *         挡不住「同一 tick 内 100 个分散目标各发一包」——那种情况下 12 个名额瞬间填满，
 *         之后每一包都在回收最老那道，形成高频闪烁。限速让新建节奏摊到时间轴上，
 *         观感变成「雷一道接一道地劈下来」而不是「一帧内炸出一片又立刻互相顶掉」。</li>
 * </ol>
 * <p>
 * <b>触发第 2、3 条闸时不是丢弃，而是「回收最老的一道」</b>
 * （{@link #reseatRedLightning}）：把那道已经最接近消散的雷搬到新落点并重置生命周期。
 * 于是玩家看到的仍然是「新位置有雷劈下来」，只是总数被压住了。
 * 外形种子 {@link AoeEffect#seed} 在回收时<b>保持不变</b>——这点很重要，
 * 它保证被回收的那道不会在搬家的同时还换一副长相，那样会读作两道雷而不是一道。
 * </p>
 * <p>
 * <b>这仍然只是客户端侧的兜底。</b>真正的源头节流应该在服务端做——见
 * {@code CarianStyleEffects.redLightning(ServerLevel, Entity)} 的按实体节流，
 * 那一层能直接省掉发包与带宽；本层负责的是「万一服务端那层被绕过（老调用点、
 * 其它附魔直接调裸坐标重载）也不至于把客户端打崩」。
 * </p>
 * <p>
 * <b>调参提示：</b>若将来新增「短时间内会大量并发」的演出类型，应优先给该类型
 * 加同位置合并 + 专用上限（照抄本节的三段式），而不是回头调大 {@link #MAX_ACTIVE}——
 * 合并能同时省下渲染开销与视觉噪声，调大上限只会两者都增加。
 * </p>
 *
 * @author RoinFlam
 * @version 6.6
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class AoeEffectManager {

    /**
     * 存活特效上限（极端情况下防止无限堆积）。
     * <p>
     * v6.2：由 64 下调至 {@value}。渲染端已接入 {@link VisualLod} 按距离 / 拥挤度削减顶点，
     * 但那削不掉「同屏 N 个独立特效」的固定调用开销，故仍需一个偏紧的上限配合。
     * </p>
     * <p>
     * v6.6：红闪另有 {@link #MAX_RED_LIGHTNING} 这道<b>更紧的专用上限</b>，
     * 因此本值现在主要约束的是「多种演出同时触发」的场景，红闪一家已经吃不满它了。
     * </p>
     */
    private static final int MAX_ACTIVE = 40;

    /**
     * 死亡演出（猩红立体花 SCARLET_BLOOM / 癫火扩散 FRENZIED_FLAME）整体尺寸放大倍数。
     * <p>基础半径 5.0f × {@code 1.5} = 7.5f（主体直径约 15 格；注意「爆发冲击环」为半径×1.9，
     * 故环直径约 28 格——这是观感上最占视野的部分）。环类基元分段数由
     * {@code AoeEffectRenderer.segmentsFor} 封顶 72，改此常量不会导致顶点爆炸，线宽随半径同步缩放。
     * <b>要继续调小只改此值即可</b>：1.2≈主体 12 格、1.0≈主体 10 格（=不额外放大、与作用范围相当）。</p>
     */
    private static final float DEAD_EFFECT_SCALE = 1.5f;

    /** 当前存活特效列表（仅客户端主线程访问） */
    private static final List<AoeEffect> ACTIVE = new ArrayList<>();

    /**
     * 红色闪电「同位置合并」判定半径（格）。
     * <p>
     * v6.6：由 2.5 放宽到 {@value}。旧值只能收敛「同一个目标在原地被反复劈」；
     * 放宽后「挤在一堆的几个目标」也会收敛成一道——4.5 格内同时存在多道闪电本来就会
     * 糊成一片，分开画属于纯浪费（详见类注释「v6.6」小节）。
     * </p>
     * <p>
     * 要让相邻目标更容易各自独立成一道就调小，要让密集战场更省就调大。
     * </p>
     */
    private static final double RED_LIGHTNING_MERGE_DIST = 4.5;

    /** {@link #RED_LIGHTNING_MERGE_DIST} 的平方（避免每次比较都开方） */
    private static final double RED_LIGHTNING_MERGE_DIST_SQR =
            RED_LIGHTNING_MERGE_DIST * RED_LIGHTNING_MERGE_DIST;

    /**
     * 红色闪电的<b>专用</b>存活上限（道）。
     * <p>
     * 与 {@link #MAX_ACTIVE} 相互独立：后者管「全部类型加起来」，而红闪一家就能吃满，
     * 导致同时触发的因果律 / 立体花被挤掉。分开限制之后红闪最多占 {@value} 席。
     * </p>
     * <p>
     * 取 12 的依据：古龙雷击极端爆发时，玩家视野里同时能<b>分辨出来</b>的雷本就只有十几道，
     * 再多完全是视觉噪声。超出后走「回收最老一道」而非丢弃，因此观感上仍是「雷一直在劈」。
     * </p>
     */
    private static final int MAX_RED_LIGHTNING = 12;

    /**
     * 红色闪电的新建限速（毫秒）：距上一次<b>真正新建</b>不足该间隔时，改为回收最老的一道。
     * <p>
     * 前两道闸（合并半径、数量上限）都是空间维度的，挡不住「同一 tick 内 100 个分散目标
     * 各发一包」——那种情况下 12 个名额瞬间填满，之后每一包都在回收最老那道，形成高频闪烁。
     * 本闸把新建节奏摊到时间轴上，观感变成「雷一道接一道地劈下来」。
     * </p>
     * <p>
     * 取 {@value} ms（约 0.9 tick）：既能压住单帧爆发，又不会让正常节奏的连续落雷显得迟滞。
     * </p>
     */
    private static final long RED_LIGHTNING_SPAWN_COOLDOWN_MS = 45L;

    /**
     * 上一次<b>真正新建</b>红闪的墙钟时刻（毫秒），供 {@link #RED_LIGHTNING_SPAWN_COOLDOWN_MS} 限速。
     * <p>注意：合并 / 回收<b>不</b>刷新本值——限速约束的是「新增一道」这个动作，
     * 而合并与回收都没有增加总数。</p>
     */
    private static long lastRedLightningSpawnMs = 0L;

    private AoeEffectManager() {
    }

    /**
     * 一个正在播放的定点特效实例。
     * <p>动画进度由墙钟 age 驱动（与世界 tick 解耦，避免取模回绕等问题）：
     * progress = (now - birthMs) / durationMs。</p>
     */
    public static final class AoeEffect {
        /** 特效类型（见 {@link AoeEffectPacket}） */
        public final int type;
        /**
         * 绑定实体 id；{@link AoeEffectPacket#NO_ENTITY}(-1) 表示定点不跟随。
         * <p>
         * <b>v6.1：去掉 final。</b>绑定实体死亡 / 卸载时，{@link #detachDeadBindings}
         * 会把本字段改写为 {@link AoeEffectPacket#NO_ENTITY} 实现<b>永久解绑</b>，
         * 防止玩家复活后（实体网络 id 不变）特效重新吸附并跟随移动，
         * 详见类注释「v6.1 修复」小节。
         * </p>
         */
        public int entityId;
        /**
         * 世界坐标。
         * <p>定点特效：恒为发包坐标。跟随特效：每帧由渲染器更新为实体的实时插值位置，作为
         * 「最后已知坐标」——实体死亡 / 移除后渲染器即用此坐标继续播放剩余演出（残留在原地），
         * 故去掉 final。</p>
         */
        public double x;
        public double y;
        public double z;
        /** 半径（格） */
        public final float radius;
        /**
         * 固定外形种子（当前仅红色闪电使用）。
         * <p>由管理器在创建特效时生成、整段生命周期内不变——即便红闪因「同位置合并」或
         * 「回收最老一道」被反复重置 {@link #birthMs}，其外形也由本字段恒定决定，不会逐次跳变。</p>
         */
        public final long seed;
        /**
         * 诞生墙钟时刻（毫秒）。
         * <p>红色闪电存在「同位置合并续命」与「超限回收」：重复落雷到同一处（或触发限流）时
         * 不新建特效，而是把已存在那道的本字段重置为当前时刻以延长播放
         * （表现为一道持续劈着的雷），故去掉 final。</p>
         */
        public long birthMs;
        /** 总时长（毫秒） */
        public final long durationMs;

        AoeEffect(int type, int entityId, double x, double y, double z,
                  float radius, long seed, long birthMs, long durationMs) {
            this.type = type;
            this.entityId = entityId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.seed = seed;
            this.birthMs = birthMs;
            this.durationMs = durationMs;
        }
    }

    /**
     * 创建一个定点特效（由网络包在客户端主线程调用，历史签名）。
     * <p>等价于 {@code spawn(type, x, y, z, radius, AoeEffectPacket.NO_ENTITY)}，即不跟随。</p>
     *
     * @param type   特效类型
     * @param x      世界坐标 X
     * @param y      世界坐标 Y
     * @param z      世界坐标 Z
     * @param radius 半径（格）
     */
    public static void spawn(int type, double x, double y, double z, float radius) {
        spawn(type, x, y, z, radius, AoeEffectPacket.NO_ENTITY);
    }

    /**
     * 创建一个特效（可绑定实体跟随，由网络包在客户端主线程调用）。
     * <p><b>红色闪电特例：</b>古龙雷击等会每数 tick 对同一目标重复降雷，若每次都新建特效，同一处会
     * 同时叠着多道形态各异的闪电并不断新生，视觉上呈高频「鬼畜」跳变。故红闪在新建前先经过
     * {@link #tryAbsorbRedLightning 三重节流}：命中任一条闸时只合并 / 回收、不新建。</p>
     *
     * @param type     特效类型
     * @param x        世界坐标 X
     * @param y        世界坐标 Y
     * @param z        世界坐标 Z
     * @param radius   半径（格）
     * @param entityId 绑定实体 id；{@link AoeEffectPacket#NO_ENTITY}(-1) 为定点，{@code >=0} 为跟随
     */
    public static void spawn(int type, double x, double y, double z, float radius, int entityId) {
        spawn(type, x, y, z, radius, entityId, AoeEffectPacket.AUTO_DURATION);
    }

    /**
     * 创建一个特效（可绑定实体跟随、可指定播放时长；v6.5 新增时长参数）。
     * <p>
     * <b>时长参数的意义：</b>绝大多数演出的机制时长是固定的，客户端按类型查
     * {@link #durationFor} 即可。但<b>满月月华</b>不是——{@code EnchantmentFullMoon}
     * 的回血持续时间取决于持有者有没有装备暗月（200 或 400 tick），
     * 客户端无从得知，只能取最长的写死，导致不带暗月时特效比实际回血多播一大截。
     * 现在由服务端把实际时长发下来，二者严格对齐（详见类注释「v6.5」小节）。
     * </p>
     *
     * @param type       特效类型
     * @param x          世界坐标 X（跟随特效用作初始 / 实体消失后的回退坐标）
     * @param y          世界坐标 Y
     * @param z          世界坐标 Z
     * @param radius     半径（格）
     * @param entityId   绑定实体 id；{@link AoeEffectPacket#NO_ENTITY}(-1) 为定点
     * @param durationMs 播放时长（毫秒）；{@link AoeEffectPacket#AUTO_DURATION}(-1) 为按类型取默认值
     */
    public static void spawn(int type, double x, double y, double z, float radius,
                             int entityId, int durationMs) {
        long now = System.currentTimeMillis();
        // ⭐ v6.6：红闪三重节流（合并半径 / 专用上限 / 新建限速），命中任一条即不新建
        if (type == AoeEffectPacket.TYPE_RED_LIGHTNING && tryAbsorbRedLightning(x, y, z, now)) {
            return;
        }
        // v6.1：死亡演出（猩红立体花 / 癫火扩散）半径按 scaleFor 放大后再存储；
        // CULL 裁剪与渲染均使用放大后半径，全链一致，避免大花被误裁
        float scaledRadius = radius * scaleFor(type);
        // v6.5：服务端指定了时长就用它，否则按类型取默认值
        long duration = (durationMs > 0) ? durationMs : durationFor(type);
        ACTIVE.add(new AoeEffect(type, entityId, x, y, z, scaledRadius, makeSeed(now, x, z), now, duration));
        // 上限保护：超出则丢弃最早的（对红闪而言最早那道也最接近消散，观感损失最小）
        while (ACTIVE.size() > MAX_ACTIVE) {
            ACTIVE.remove(0);
        }
    }

    /**
     * 红色闪电的三重节流：合并半径 → 专用数量上限 → 新建限速。
     * <p>
     * 返回 {@code true} 表示本次<b>不应新建</b>（已通过合并或回收消化掉），
     * 返回 {@code false} 表示放行、允许新建一道。三条闸的动机与取值理由详见类注释「v6.6」小节。
     * </p>
     * <p>
     * <b>只遍历一趟。</b>本方法在同一次遍历中同时取出三样东西：距新落点最近的那道、
     * 最老的那道、以及红闪总数。这样即便三条闸全部要判断，遍历成本也只有一次
     * （{@link #ACTIVE} 最长 {@value #MAX_ACTIVE} 项，且每帧至多被调用几次）。
     * </p>
     * <p>
     * <b>为什么触发限流时是「回收」而不是「丢弃」：</b>直接 return 会让玩家看到
     * 「雷劈在了别处、这里什么都没有」，与机制不符（那个目标确实挨了雷）。
     * 回收最老那道并搬到新落点，玩家看到的仍是「新位置有雷劈下来」，
     * 只是总数被压住了；而被搬走的那道本就最接近消散，损失最小。
     * </p>
     *
     * @param x   新落点世界坐标 X
     * @param y   新落点世界坐标 Y
     * @param z   新落点世界坐标 Z
     * @param now 当前墙钟（毫秒）
     * @return true 表示已消化、调用方不应再新建
     */
    private static boolean tryAbsorbRedLightning(double x, double y, double z, long now) {
        AoeEffect nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        AoeEffect oldest = null;
        int count = 0;

        for (int i = 0; i < ACTIVE.size(); i++) {
            AoeEffect fx = ACTIVE.get(i);
            if (fx.type != AoeEffectPacket.TYPE_RED_LIGHTNING) {
                continue;
            }
            count++;
            double dx = fx.x - x;
            double dz = fx.z - z;
            double distSqr = dx * dx + dz * dz;
            if (distSqr < nearestSqr) {
                nearestSqr = distSqr;
                nearest = fx;
            }
            if (oldest == null || fx.birthMs < oldest.birthMs) {
                oldest = fx;
            }
        }

        // ===== 闸 1：同位置合并 =====
        // 附近已有一道，直接把它搬到新落点并续命，表现为「一道持续劈着的雷」
        if (nearest != null && nearestSqr <= RED_LIGHTNING_MERGE_DIST_SQR) {
            reseatRedLightning(nearest, x, y, z, now);
            return true;
        }

        // ===== 闸 2 / 3：数量上限与新建限速 =====
        // 二者的处理方式相同（回收最老一道），故合并判断
        boolean overCap = count >= MAX_RED_LIGHTNING;
        boolean tooSoon = (now - lastRedLightningSpawnMs) < RED_LIGHTNING_SPAWN_COOLDOWN_MS;
        if (overCap || tooSoon) {
            if (oldest != null) {
                reseatRedLightning(oldest, x, y, z, now);
                return true;
            }
            // oldest 为 null 说明当前一道红闪都没有：
            // 此时 overCap 不可能成立（count 必为 0），只可能是 tooSoon。
            // 上一道刚消失、这一道理应立刻劈下来，故放行——否则「雷断档」比「雷太多」更违和。
        }

        // ===== 放行：真正新建一道 =====
        // 只有走到这里才刷新限速时间戳——合并与回收都没有增加总数，不应占用新建配额
        lastRedLightningSpawnMs = now;
        return false;
    }

    /**
     * 把一道已存在的红闪搬到新落点并重置生命周期（合并 / 回收共用）。
     * <p>
     * <b>刻意不改 {@link AoeEffect#seed}：</b>外形种子决定电柱的蜿蜒形状与分叉分布，
     * 保持不变才能读作「同一道雷换了个地方继续劈」；一旦同时换位置又换长相，
     * 玩家会读成「这道消失了、那边新出了一道」，反而更闪。
     * </p>
     *
     * @param fx  被搬动的红闪
     * @param x   新落点 X
     * @param y   新落点 Y
     * @param z   新落点 Z
     * @param now 当前墙钟（毫秒）
     */
    private static void reseatRedLightning(@Nonnull AoeEffect fx, double x, double y, double z, long now) {
        fx.x = x;
        fx.y = y;
        fx.z = z;
        fx.birthMs = now;
    }

    /**
     * 生成一个非 0 的固定外形种子（散列自创建时刻与落点坐标）。
     * <p>存入 {@link AoeEffect#seed} 后整段生命周期不变，使每道特效外形稳定、不同特效外形各异。</p>
     *
     * @param now 创建时墙钟（毫秒）
     * @param x   落点世界坐标 X
     * @param z   落点世界坐标 Z
     * @return 非 0 种子
     */
    private static long makeSeed(long now, double x, double z) {
        long bits = now * 1099511628211L
                ^ Double.doubleToLongBits(x) * 31
                ^ Double.doubleToLongBits(z) * 17;
        bits ^= (bits >>> 33);
        return bits == 0 ? 0x9E3779B97F4A7C15L : bits;
    }

    /**
     * 各类型特效尺寸放大系数。
     * <p>猩红立体花 / 癫火扩散为死亡高潮演出，整体放大以匹配其分量；其余特效保持原尺寸。</p>
     * <p>
     * 满月月华与神圣净化<b>均不放大</b>——前者的半径只决定脚下回春环与月光池
     * （月轮与月华柱的高度由渲染器常量控制，与本半径无关），放大会让光池比人大一大圈、
     * 读作「站在法阵里」而非「被月光笼罩」；后者是单体技的命中反馈，
     * 放大会被误读成 AOE、让玩家以为周围亡灵也吃到了伤害。
     * </p>
     *
     * @param type 特效类型
     * @return 半径放大倍数（1.0 为不放大）
     */
    private static float scaleFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return DEAD_EFFECT_SCALE;
            default:
                return 1.0f;
        }
    }

    /**
     * 各类型特效时长（毫秒）。
     *
     * @param type 特效类型
     * @return 时长
     */
    private static long durationFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_CAUSALITY:
                return 1100L;
            case AoeEffectPacket.TYPE_FROST_QUAKE:
                return 1000L;
            case AoeEffectPacket.TYPE_REPULSION:
                return 520L;
            case AoeEffectPacket.TYPE_RED_LIGHTNING:
                // 龙雷红色闪电：还原原作——巨大亮眼、持续强烈明灭、消散较慢（线性映射，无分段）
                return 1400L;
            case AoeEffectPacket.TYPE_MOON_BLESSING:
                // v6.5：本值只是「服务端没指定时长」时的回退。
                // 正常情况下 EnchantmentFullMoon 会把实际回血时长（200 或 400 tick）
                // 随包发下来，由 spawn 的 durationMs 参数覆盖本值——
                // 因此不带暗月时演出正好 10 秒、带暗月时正好 20 秒，与回血严格对齐。
                //
                // 回退值取 10000 而非 20000：万一时长丢失，宁可短一点也不要
                // 在玩家满血之后还挂着月光（那正是 v6.4 被反馈的问题）。
                //
                // ⚠ 渲染器不再需要同步任何时长常量——它直接用
                // 「已播放毫秒 / 总毫秒」算绝对秒数，动画速度天然与总时长无关。
                return 10000L;
            case AoeEffectPacket.TYPE_SACRED_PURGE:
                // 神圣净化：「打中那一下」的瞬时反馈，与排斥同属最短一档。
                // 做长了会让连续攻击亡灵时前后两次爆闪叠成一片，反而看不清打中了几下。
                return 700L;
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
                // 立体花总时长。配合 progressFor 的分段映射：前 1500ms（chargeUpMsFor）把 progress
                // 推进到盛放主点 0.42（恒对齐附魔 30tick 第二阶段爆发），其后 3900ms 把 progress
                // 推进到 1.0（凋谢余波段）。加长总时长只拉长凋谢、不影响盛放时机。
                // 5400 = 1500ms 绽放蓄能（对齐爆发）+ 3900ms 盛放 / 凋谢长余波
                return 5400L;
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                // 癫火总时长。同样经 progressFor 分段：前 1500ms 推进到爆发主点 0.42（对齐 30tick），
                // 其后 3900ms 推进到 1.0（焦黑余烬段）。
                // 5400 = 1500ms 狂乱蓄能（对齐爆发）+ 3900ms 爆发 / 余烬长尾
                return 5400L;
            default:
                return 700L;
        }
    }

    /**
     * 死亡演出（猩红立体花 / 癫火扩散）的「蓄能段绝对时长」（毫秒）。
     * <p>蓄能段（花苞缓慢绽放 → 盛放前一刻）固定占用这段绝对时间，使盛放主点恒定对齐附魔
     * 30tick(1500ms) 的第二阶段爆发——无论总时长 {@link #durationFor} 调多长，盛放时机都不漂移；
     * 加长只拉长盛放之后的爆发 / 凋谢余波。其余类型返回 0（表示按总时长线性播放，无分段）。</p>
     * <p>
     * 满月月华与神圣净化都返回 0（线性）。满月虽然也是「跟随 + 数秒」的演出，
     * 但它没有需要对齐的延迟触发点——复活是<b>即时</b>生效的
     * （{@code evt.setCanceled(true)} 那一瞬间），
     * 不像猩红罗妮亚那样有 30tick 的拉取无敌前摇要等。
     * </p>
     *
     * @param type 特效类型
     * @return 蓄能段绝对时长（毫秒）；0 表示线性播放
     */
    private static long chargeUpMsFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return 1500L; // 对齐附魔 30tick 第二阶段爆发
            default:
                return 0L;
        }
    }

    /**
     * 死亡演出盛放主点的归一化进度值（蓄能段结束时 progress 恰好到达此值）。
     * <p>与渲染器 {@code drawScarletBloom} / {@code drawFrenziedFlame} 时间轴里的盛放主点
     * （p≈0.42）保持一致：蓄能段把 progress 从 0 推进到 0.42，剩余时长把 progress 从 0.42
     * 推进到 1.0。这样渲染器时间轴的所有归一化魔数（绽放 / 盛放 / 爆发 / 凋谢分界）<b>无需改动</b>，
     * 仅靠分段 progress 即可在「盛放对齐 30tick」前提下任意拉长总时长。</p>
     *
     * @param type 特效类型
     * @return 盛放主点归一化值；分段无效时返回 0
     */
    private static float bloomPointFor(int type) {
        switch (type) {
            case AoeEffectPacket.TYPE_SCARLET_BLOOM:
            case AoeEffectPacket.TYPE_FRENZIED_FLAME:
                return 0.42f;
            default:
                return 0f;
        }
    }

    /**
     * 计算某特效当前的归一化播放进度 progress∈[0,1]（供渲染器调用）。
     * <p>死亡演出（{@link #chargeUpMsFor} &gt; 0）采用<b>分段</b>映射：前 {@code chargeUp} 毫秒把
     * progress 从 0 线性推进到盛放主点 {@link #bloomPointFor}（恒对齐 30tick 爆发），其后用剩余
     * 时长把 progress 从盛放主点线性推进到 1.0（凋谢余波段，随总时长加长而拉长）。其余类型按总
     * 时长线性映射。这样「加长特效」只延长盛放之后的余波，盛放时机与附魔机制始终同步。</p>
     * <p>
     * <b>满月月华走的是线性映射</b>，渲染器再用
     * {@code age = progress × 总秒数} 把它换算回绝对秒数——
     * 因此那边的全部动画速度都以「弧度/秒」「圈/秒」表达，与总时长无关。
     * </p>
     *
     * @param fx  特效实例
     * @param now 当前墙钟（毫秒）
     * @return 归一化进度，夹取到 [0,1]
     */
    public static float progressFor(AoeEffect fx, long now) {
        long elapsed = now - fx.birthMs;
        if (elapsed <= 0L) {
            return 0f;
        }
        long chargeUp = chargeUpMsFor(fx.type);
        if (chargeUp <= 0L) {
            // 线性映射（非死亡类型）
            return clamp01(elapsed / (float) fx.durationMs);
        }
        float bloom = bloomPointFor(fx.type);
        if (elapsed < chargeUp) {
            // 蓄能段：0 → 盛放主点
            return bloom * (elapsed / (float) chargeUp);
        }
        // 凋谢段：盛放主点 → 1.0
        long rest = fx.durationMs - chargeUp;
        if (rest <= 0L) {
            return 1f;
        }
        return bloom + (1f - bloom) * clamp01((elapsed - chargeUp) / (float) rest);
    }

    /**
     * 夹取到 0~1。
     *
     * @param v 输入
     * @return 夹取结果
     */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    /**
     * 客户端 tick 末尾：解绑已死亡 / 已卸载的绑定实体，推进并移除到期特效；离开世界时清空。
     * <p>
     * v6.1：新增 {@link #detachDeadBindings} 调用，修复「大灭」触发期间死亡、复活后特效
     * 重新吸附并跟着玩家跑的问题（详见类注释）。
     * </p>
     *
     * @param event tick 事件
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        if (ACTIVE.isEmpty()) {
            return;
        }
        // ⭐ v6.1：先解绑死亡 / 卸载的绑定实体，再做到期清理
        detachDeadBindings(level);
        long now = System.currentTimeMillis();
        ACTIVE.removeIf(fx -> now - fx.birthMs >= fx.durationMs);
    }

    /**
     * 把绑定实体已死亡 / 已卸载的特效<b>永久解绑</b>（{@code entityId} 置为
     * {@link AoeEffectPacket#NO_ENTITY}）。
     * <p>
     * <b>为什么必须永久解绑而不是每帧判断存活：</b>Minecraft 玩家复活后实体网络 id 不变，
     * 若渲染器每帧都拿 id 重新解析实体，死亡时特效脱离、复活时又会重新吸附，
     * 表现为「大灭特效跟着复活后的玩家跑」。置为 {@link AoeEffectPacket#NO_ENTITY} 后，
     * 渲染器的 {@code if (fx.entityId >= 0)} 分支不再成立，
     * 直接使用 {@code fx.x/y/z}（实体存活期间由渲染器每帧写回的最后一帧插值坐标），
     * 剩余的凋谢 / 余烬段留在死亡点原地播完，与设计一致。
     * </p>
     * <p>
     * <b>满月月华的情形正好相反、但结论一致：</b>满月是「阻止死亡」，
     * 持有者在这 20 秒里理应始终存活。若他在演出期间又被补刀打死了，
     * 本方法会把月华解绑、留在死亡点播完剩余部分——
     * 这恰好是正确表现：月光洒在他倒下的地方，而不是跟着复活点的新身体跑。
     * </p>
     * <p>
     * 实体临时离开视野（区块卸载）同样会触发解绑——这与原本的「最后已知坐标」回退路径行为一致。
     * </p>
     *
     * @param level 客户端世界（非 null）
     */
    private static void detachDeadBindings(@Nonnull Level level) {
        for (int i = 0; i < ACTIVE.size(); i++) {
            AoeEffect fx = ACTIVE.get(i);
            if (fx.entityId == AoeEffectPacket.NO_ENTITY) {
                continue;
            }
            Entity bound = level.getEntity(fx.entityId);
            if (bound == null || !bound.isAlive()) {
                fx.entityId = AoeEffectPacket.NO_ENTITY;
            }
        }
    }

    /**
     * @return 当前存活特效列表（渲染线程只读）
     */
    public static List<AoeEffect> getActive() {
        return ACTIVE;
    }

    /**
     * 清空（离开世界等场景）。
     * <p>v6.6：一并复位红闪的新建限速时间戳——否则重进世界后第一道雷可能被上一局残留的
     * 时间戳卡掉（虽然只卡 45ms，但没有理由留这个悬空状态）。</p>
     */
    public static void clear() {
        ACTIVE.clear();
        lastRedLightningSpawnMs = 0L;
    }
}
