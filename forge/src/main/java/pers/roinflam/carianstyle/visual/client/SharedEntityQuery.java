package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * 相机附近实体的每帧共享查询缓存（纯客户端）。
 * <p>
 * <b>解决的问题：</b>五个实体类特效渲染器（猩红腐败雾 / 冻伤冰雾 / 出血飙血 / 黄金树祝福 / 重力压制）
 * 每帧都各自调用一次
 * {@code mc.level.getEntitiesOfClass(LivingEntity.class, 相机 ±48 格的 AABB, 各自的判定条件)}，
 * 而重力压制渲染器更是要查两次（受压制者 + 施法者），合计每帧 <b>6 次</b>范围实体查询。
 * 五者的查询范围完全相同（{@code CULL = 48.0}），差异只在于「筛选条件」。
 * </p>
 * <p>
 * <b>做法：</b>本类每帧只做<b>一次</b>范围查询，条件放宽为「存活的 {@link LivingEntity}」，
 * 结果按帧序号（{@link VisualBatch#frameId()}）缓存；各渲染器改为遍历这份共享列表、
 * 在循环内部用 {@code continue} 做自己的筛选。相比原先每个渲染器都新建一个
 * {@link java.util.ArrayList}，现在整帧只分配一个列表。
 * </p>
 * <p>
 * <b>为什么放宽为「存活」而不是把各家条件合并：</b>合并条件需要各渲染器把判定逻辑注册进来，
 * 耦合过重；而范围查询真正昂贵的是<b>遍历 AABB 覆盖的实体分区并做包围盒相交测试</b>这一段，
 * 与筛选条件无关。把这段从 6 次降为 1 次即拿到绝大部分收益，各渲染器循环内的条件判断
 * （{@code hasEffect} 等）本质是一次 HashMap 查询，开销可忽略。
 * </p>
 * <p>
 * <b>范围一致性（重要）：</b>{@link #QUERY_RANGE} 必须 <b>≥</b> 各渲染器自己的 {@code CULL} 常量，
 * 否则会漏掉本应显示的实体。当前五者的 {@code CULL} 均为 48.0，与本值相同。
 * 各渲染器循环内仍保留自己的精确平方距离裁剪（AABB 为立方体，对角线比 48 更远，需要再剔除一次），
 * 该行为与优化前完全一致。<b>若将来调大某个渲染器的 {@code CULL}，务必同步调大本值。</b>
 * </p>
 *
 * <h3>v2 新增：辉剑投射物的独立缓存</h3>
 * <p>
 * {@link GlintbladesEffectRenderer} 此前是<b>唯一绕过本类、自行做范围查询</b>的世界渲染器，
 * 原因是类型不匹配——辉剑 {@link EntityGlintblades} 继承自
 * {@code ThrowableProjectile}，不是 {@link LivingEntity}，拿不到上面那份共享列表。
 * </p>
 * <p>
 * 于是它自己开了一次 {@code getEntitiesOfClass}，且查询半径是 64（比生物那份的 48 更大，
 * 因为巨剑阵的剑 {@code size} 可达 7.5、视觉体量大得多，需要更早开始渲染）。
 * </p>
 * <p>
 * 本次给它单开一份<b>同样按帧号缓存</b>的列表（{@link #glintbladesNearCamera}）。
 * 收益不在于「省掉一次查询」——它本来每帧也只查一次；真正的收益是：
 * </p>
 * <ul>
 *     <li><b>一致性</b>：全部世界渲染器现在都从同一个入口取实体列表，
 *         将来若要给辉剑加第二个渲染器（例如命中特效），不会又冒出第三次查询；</li>
 *     <li><b>可复用</b>：帧级缓存意味着同一帧内多次调用只查一次；</li>
 *     <li><b>范围集中管理</b>：{@link #PROJECTILE_QUERY_RANGE} 与生物那份的
 *         {@link #QUERY_RANGE} 并列写在这里，改动时一眼能看出两者的关系，
 *         不至于像以前那样一个写在渲染器里、一个写在这里、对不上都没人发现。</li>
 * </ul>
 * <p>
 * <b>为什么不做成泛型缓存：</b>泛型版需要按 {@code Class} 做键、每帧至少一次 Map 查询，
 * 且为了避免装箱还要额外设计。而本模组的非生物渲染目标<b>只有辉剑一种</b>，
 * 直接写死两份缓存字段更简单、更快、也更容易读懂。若将来真出现第三类查询目标，
 * 再考虑抽象也不迟——那时的需求会比现在清楚。
 * </p>
 * <p>
 * 仅在客户端渲染线程访问，无并发问题。
 * </p>
 *
 * @author FlameForge
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
public final class SharedEntityQuery {

    /**
     * 生物共享查询半径（格）。
     * <p>必须 ≥ 所有使用方渲染器的 {@code CULL} 常量，详见类注释的「范围一致性」说明。</p>
     */
    public static final double QUERY_RANGE = 48.0;

    /**
     * 辉剑投射物共享查询半径（格）。
     * <p>
     * 比生物那份（{@link #QUERY_RANGE}）更大：巨剑阵的剑 {@code size} 可达 7.5，
     * 视觉体量远超普通实体，需要更早开始渲染，否则会出现「巨剑飞到眼前才凭空出现」。
     * </p>
     * <p>
     * <b>必须 ≥ {@code GlintbladesEffectRenderer.CULL}</b>，否则会漏掉本应显示的辉剑。
     * </p>
     */
    public static final double PROJECTILE_QUERY_RANGE = 64.0;

    /** 生物缓存对应的帧序号；与 {@link VisualBatch#frameId()} 不一致时视为过期 */
    private static int cachedFrameId = -1;

    /** 本帧生物查询结果（只读使用） */
    private static List<LivingEntity> cached = Collections.emptyList();

    /** 辉剑缓存对应的帧序号（与生物缓存各自独立，因为二者不一定都被调用） */
    private static int cachedBladeFrameId = -1;

    /** 本帧辉剑查询结果（只读使用） */
    private static List<EntityGlintblades> cachedBlades = Collections.emptyList();

    private SharedEntityQuery() {
    }

    /**
     * 取本帧「相机 {@link #QUERY_RANGE} 格立方范围内、存活的全部生物」列表。
     * <p>同一帧内多次调用只查询一次，后续调用直接返回缓存。</p>
     * <p><b>调用方须自行完成两件事：</b>
     * <ol>
     *     <li>在循环内做本渲染器的特效判定（如 {@code hasEffect}），不满足则 {@code continue}；</li>
     *     <li>做精确的平方距离裁剪（AABB 是立方体，角落比半径更远）。</li>
     * </ol>
     * 二者与优化前各渲染器的行为逐条对应，不要省略。</p>
     * <p>返回的列表<b>请勿修改</b>，它会被本帧其余渲染器共享。</p>
     *
     * @param mc  Minecraft 实例
     * @param cam 相机世界坐标
     * @return 存活生物列表；世界不可用时为空列表
     */
    @Nonnull
    public static List<LivingEntity> livingEntitiesNearCamera(@Nonnull Minecraft mc, @Nonnull Vec3 cam) {
        int frame = VisualBatch.frameId();
        if (frame == cachedFrameId) {
            return cached;
        }
        cachedFrameId = frame;

        if (mc.level == null) {
            cached = Collections.emptyList();
            return cached;
        }

        AABB box = new AABB(
                cam.x - QUERY_RANGE, cam.y - QUERY_RANGE, cam.z - QUERY_RANGE,
                cam.x + QUERY_RANGE, cam.y + QUERY_RANGE, cam.z + QUERY_RANGE
        );
        cached = mc.level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive);
        return cached;
    }

    /**
     * 取本帧「相机 {@link #PROJECTILE_QUERY_RANGE} 格立方范围内、未被移除的全部魔法辉剑」列表。
     * <p>同一帧内多次调用只查询一次，后续调用直接返回缓存。</p>
     * <p>
     * <b>筛选条件用 {@code !isRemoved()} 而非 {@code isAlive()}</b>：
     * {@code Entity.isAlive()} 对投射物的语义是「未被移除」，二者在
     * {@code ThrowableProjectile} 上等价；但显式写 {@code !isRemoved()} 更贴合投射物的语义，
     * 也与优化前 {@link GlintbladesEffectRenderer} 自查时的谓词逐字一致，避免行为漂移。
     * </p>
     * <p><b>调用方仍须自行做精确的平方距离裁剪</b>（AABB 是立方体，角落比半径更远）。</p>
     * <p>返回的列表<b>请勿修改</b>。</p>
     *
     * @param mc  Minecraft 实例
     * @param cam 相机世界坐标
     * @return 辉剑列表；世界不可用时为空列表
     */
    @Nonnull
    public static List<EntityGlintblades> glintbladesNearCamera(@Nonnull Minecraft mc, @Nonnull Vec3 cam) {
        int frame = VisualBatch.frameId();
        if (frame == cachedBladeFrameId) {
            return cachedBlades;
        }
        cachedBladeFrameId = frame;

        if (mc.level == null) {
            cachedBlades = Collections.emptyList();
            return cachedBlades;
        }

        AABB box = new AABB(
                cam.x - PROJECTILE_QUERY_RANGE, cam.y - PROJECTILE_QUERY_RANGE, cam.z - PROJECTILE_QUERY_RANGE,
                cam.x + PROJECTILE_QUERY_RANGE, cam.y + PROJECTILE_QUERY_RANGE, cam.z + PROJECTILE_QUERY_RANGE
        );
        cachedBlades = mc.level.getEntitiesOfClass(EntityGlintblades.class, box, e -> !e.isRemoved());
        return cachedBlades;
    }

    /**
     * 清空缓存（离开世界等场景可调用，非必需——帧序号不匹配时本就会重新查询）。
     */
    public static void clear() {
        cachedFrameId = -1;
        cached = Collections.emptyList();
        cachedBladeFrameId = -1;
        cachedBlades = Collections.emptyList();
    }
}
