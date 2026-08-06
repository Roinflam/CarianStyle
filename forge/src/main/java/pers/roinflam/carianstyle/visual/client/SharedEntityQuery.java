package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * 相机附近生物的每帧共享查询缓存（纯客户端）。
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
 * <p>
 * 仅在客户端渲染线程访问，无并发问题。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class SharedEntityQuery {

    /**
     * 共享查询半径（格）。
     * <p>必须 ≥ 所有使用方渲染器的 {@code CULL} 常量，详见类注释的「范围一致性」说明。</p>
     */
    public static final double QUERY_RANGE = 48.0;

    /** 缓存对应的帧序号；与 {@link VisualBatch#frameId()} 不一致时视为过期 */
    private static int cachedFrameId = -1;

    /** 本帧查询结果（只读使用） */
    private static List<LivingEntity> cached = Collections.emptyList();

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
     * 清空缓存（离开世界等场景可调用，非必需——帧序号不匹配时本就会重新查询）。
     */
    public static void clear() {
        cachedFrameId = -1;
        cached = Collections.emptyList();
    }
}
