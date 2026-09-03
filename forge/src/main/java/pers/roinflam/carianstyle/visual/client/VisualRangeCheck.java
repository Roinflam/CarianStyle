package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 视觉范围契约的<b>启动期自检</b>（纯客户端，防呆用，不改变任何运行时行为）。
 *
 * <h3>要防的是什么</h3>
 * <p>
 * 本模组的世界渲染器之间存在两条<b>隐性契约</b>，此前只靠注释约束：
 * </p>
 * <ol>
 *     <li><b>共享查询契约</b>——{@link SharedEntityQuery#QUERY_RANGE}（当前 48 格）
 *         必须 <b>≥</b> 每个使用方渲染器自己的 {@code CULL} 常量。
 *         共享查询只捞回相机 {@code QUERY_RANGE} 格立方范围内的生物，
 *         渲染器却按自己的 {@code CULL} 做距离裁剪；一旦 {@code CULL > QUERY_RANGE}，
 *         那段差值区间里的实体<b>根本没进列表</b>，特效直接不显示。
 *         辉剑走另一份缓存，对应上限是 {@link SharedEntityQuery#PROJECTILE_QUERY_RANGE}；</li>
 *     <li><b>光环扫描契约</b>——{@code AuraScanner.SCAN_RANGE} 必须 <b>≥</b>
 *         {@code AuraGroundRenderer.RENDER_CULL}。扫描器只产出扫描半径内实体的光环，
 *         渲染裁剪比扫描范围大的那一段拿不到任何数据。</li>
 * </ol>
 * <p>
 * <b>这类错误的特征是「静默」</b>：不报错、不崩溃、不卡顿，只是某个距离段的特效凭空消失，
 * 而且只有在正好站到那个距离上才看得出来。改一个常量、几周后才有人反馈「远处看不到血滴」，
 * 排查成本极高。本类把它变成一条开服即可见的日志。
 * </p>
 *
 * <h3>为什么用反射读常量，而不是把 CULL 改成 public</h3>
 * <p>
 * 后者要改 15 个渲染器文件、扩大 15 处的可见性，而收益只是省掉这里的一点反射代码——
 * 得不偿失，也违背最小改动。各渲染器的 {@code CULL} 是
 * {@code private static final double} 编译期常量，虽然在使用点会被 javac 内联，
 * 但字段本身仍带 {@code ConstantValue} 属性留在 class 文件里，反射读取完全可靠。
 * </p>
 * <p>
 * 反射只在客户端初始化时跑一次、总共十几次字段读取，开销可忽略；
 * 运行时的渲染热路径一行都没碰。
 * </p>
 *
 * <h3>为什么只记日志、不抛异常</h3>
 * <p>
 * 范围写错的后果是「远处特效不显示」，而抛异常的后果是「整个客户端起不来」——
 * 后者严重得多。对玩家而言前者可以忍，对开发者而言一条 ERROR 日志已经足够醒目。
 * 若希望在开发环境直接炸出来，把 {@link #report} 里的 {@code LogUtil.error}
 * 换成 {@code throw new IllegalStateException(...)} 即可。
 * </p>
 *
 * <h3>新增渲染器时要做什么</h3>
 * <p>
 * 若新渲染器调用了 {@link SharedEntityQuery#livingEntitiesNearCamera}，
 * 把它的类加进 {@link #LIVING_QUERY_CONSUMERS}；调用辉剑那份的加进
 * {@link #PROJECTILE_QUERY_CONSUMERS}。<b>漏加不会导致任何问题</b>，
 * 只是这条契约少了一层保护——本类本身不参与渲染，加不加都不影响画面。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
public final class VisualRangeCheck {

    // ==================== 字段名常量 ====================

    /** 各渲染器距离裁剪常量的字段名 */
    private static final String FIELD_CULL = "CULL";

    /** 光环地面渲染器的裁剪常量字段名（与其余渲染器不同名） */
    private static final String FIELD_RENDER_CULL = "RENDER_CULL";

    /** 光环扫描器的扫描半径字段名 */
    private static final String FIELD_SCAN_RANGE = "SCAN_RANGE";

    // ==================== 契约成员表 ====================

    /**
     * 使用「生物共享查询」（{@link SharedEntityQuery#livingEntitiesNearCamera}）的全部渲染器。
     * <p>它们的 {@code CULL} 必须 ≤ {@link SharedEntityQuery#QUERY_RANGE}。</p>
     */
    private static final Class<?>[] LIVING_QUERY_CONSUMERS = {
            BadOmenRenderer.class,
            CalamityRenderer.class,
            CarianRetaliationRenderer.class,
            DaedicarWoeRenderer.class,
            DarkMoonRenderer.class,
            FrostbiteMistRenderer.class,
            GoldenTreeBlessingRenderer.class,
            GravitasDistortionRenderer.class,
            HemorrhageBloodRenderer.class,
            HowlShabririRenderer.class,
            IncisionRenderer.class,
            ScarletRotMistRenderer.class,
            SleepRenderer.class,
            TimeReversalRenderer.class
    };

    /**
     * 使用「辉剑共享查询」（{@link SharedEntityQuery#glintbladesNearCamera}）的全部渲染器。
     * <p>它们的 {@code CULL} 必须 ≤ {@link SharedEntityQuery#PROJECTILE_QUERY_RANGE}。</p>
     */
    private static final Class<?>[] PROJECTILE_QUERY_CONSUMERS = {
            GlintbladesEffectRenderer.class
    };

    /** 已执行过自检的标记（重复调用安全） */
    private static boolean checked = false;

    private VisualRangeCheck() {
    }

    /**
     * 执行一次全部范围契约的自检。
     * <p>
     * 应在客户端初始化阶段（{@code FMLClientSetupEvent}）调用一次。重复调用安全——
     * 第二次起直接返回，不会重复刷日志。
     * </p>
     * <p>
     * 本方法<b>不抛出任何异常</b>：反射失败、字段被改名、类加载异常都只记日志，
     * 绝不因为一个防呆检查把客户端启动流程搞崩。
     * </p>
     */
    public static void validate() {
        if (checked) {
            return;
        }
        checked = true;

        List<String> violations = new ArrayList<>();

        // ===== 契约 1：生物共享查询 =====
        for (Class<?> consumer : LIVING_QUERY_CONSUMERS) {
            checkCull(consumer, FIELD_CULL,
                    SharedEntityQuery.QUERY_RANGE, "SharedEntityQuery.QUERY_RANGE", violations);
        }

        // ===== 契约 2：辉剑共享查询 =====
        for (Class<?> consumer : PROJECTILE_QUERY_CONSUMERS) {
            checkCull(consumer, FIELD_CULL,
                    SharedEntityQuery.PROJECTILE_QUERY_RANGE, "SharedEntityQuery.PROJECTILE_QUERY_RANGE", violations);
        }

        // ===== 契约 3：光环扫描范围 ≥ 光环渲染裁剪 =====
        // 上限本身也是私有常量，需要先反射读出来；读不到就跳过这一条（并已在 readDouble 里记过警告）。
        Double scanRange = readDouble(AuraScanner.class, FIELD_SCAN_RANGE);
        if (scanRange != null) {
            checkCull(AuraGroundRenderer.class, FIELD_RENDER_CULL,
                    scanRange, "AuraScanner.SCAN_RANGE", violations);
        }

        report(violations);
    }

    /**
     * 校验单个渲染器的裁剪常量是否未超过其数据来源的范围上限。
     *
     * @param owner      渲染器类
     * @param fieldName  该类中裁剪常量的字段名
     * @param limit      允许的上限值（格）
     * @param limitName  上限的来源名称，仅用于拼日志
     * @param violations 违规信息收集列表（发现问题时追加一条）
     */
    private static void checkCull(@Nonnull Class<?> owner, @Nonnull String fieldName,
                                  double limit, @Nonnull String limitName,
                                  @Nonnull List<String> violations) {
        Double cull = readDouble(owner, fieldName);
        if (cull == null) {
            // 字段不存在 / 被改名：readDouble 里已记警告，这里不再重复
            return;
        }
        if (cull > limit) {
            violations.add(String.format(
                    "%s.%s = %.1f 格，超过了 %s = %.1f 格，超出的 %.1f 格范围内实体不会进入查询结果，特效将不显示",
                    owner.getSimpleName(), fieldName, cull, limitName, limit, cull - limit));
        }
    }

    /**
     * 反射读取某个类的 {@code static double} 常量。
     *
     * @param owner     目标类
     * @param fieldName 字段名
     * @return 字段值；字段不存在或读取失败时返回 {@code null}（并已记录警告）
     */
    private static Double readDouble(@Nonnull Class<?> owner, @Nonnull String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getDouble(null);
        } catch (NoSuchFieldException e) {
            LogUtil.warn("视觉范围自检：%s 中找不到常量 %s，该项契约已跳过（字段被改名或删除时请同步更新 VisualRangeCheck）",
                    owner.getSimpleName(), fieldName);
            return null;
        } catch (Exception e) {
            LogUtil.warn("视觉范围自检：读取 %s.%s 失败，该项契约已跳过（%s）",
                    owner.getSimpleName(), fieldName, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 输出自检结果。
     * <p>
     * 全部通过时只出一条调试日志（受配置开关控制，正常玩家看不到）；
     * 发现违规时用 ERROR 级别 + 分隔线输出，保证在满屏启动日志里一眼能找到。
     * </p>
     *
     * @param violations 违规信息列表，空表示全部通过
     */
    private static void report(@Nonnull List<String> violations) {
        if (violations.isEmpty()) {
            LogUtil.debug("视觉范围自检通过：共享查询与光环扫描的范围契约均满足");
            return;
        }
        LogUtil.separator();
        LogUtil.error("视觉范围自检发现 %d 处范围契约违规，对应特效在远距离将静默消失：", violations.size());
        for (String violation : violations) {
            LogUtil.error("  - " + violation);
        }
        LogUtil.error("修复方式：调大对应的查询 / 扫描范围常量，或调小渲染器的裁剪常量，使前者 >= 后者");
        LogUtil.separator();
    }
}
