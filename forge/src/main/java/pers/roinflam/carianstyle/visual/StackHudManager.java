package pers.roinflam.carianstyle.visual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 客户端叠层 HUD 数据持有者（通用类，但数据仅供客户端 HUD 读取）。
 * <p>
 * 服务端通过 {@code StackDisplayPacket} 推送本地玩家的全量叠层快照，
 * 网络线程在主线程任务里调用 {@link #accept(Map)} 更新这里的列表；
 * 客户端 HUD 覆盖层每帧读取 {@link #getEntries()} 渲染。
 * <p>
 * 放在通用包的原因：双端共享的网络包 handle 方法会引用本类，
 * 而本类不引用任何客户端专有 API，故在服务端被引用也安全（accept 实际只在客户端执行）。
 *
 * <h3>v1.1：排序由「注册顺序」改为「按变化性分档」</h3>
 * <p>
 * 原实现只按 {@code serialId} 排序，也就是<b>按注册先后</b>。注册项少的时候无所谓，
 * 但显示项增加到三四十条之后，这个顺序就彻底失去意义了——
 * 一个 5 秒后就会消失的冷却倒计时，可能被排在一串「穿着就永远亮着」的常驻状态后面，
 * 而后者恰恰是玩家<b>最不需要盯着看</b>的。
 * </p>
 * <p>
 * 现改为先按下面三档排序，同档内再按 {@code serialId}（保证顺序稳定、不会每帧跳动）：
 * </p>
 * <ol>
 *     <li><b>冷却倒计时</b>（{@code cooldown=true}）—— 秒秒在变，且归零即消失，
 *         是唯一有「时间压力」的一类，必须最靠上；</li>
 *     <li><b>带进度条的叠层</b>（{@code max > 0}）—— 有明确上限，值得盯着攒；</li>
 *     <li><b>其余</b>（{@code max = 0}，只显示数字）—— 大多是「条件成立就一直亮着」的
 *         状态指示（奉剑满血、碎星档位、黑焰庇护减伤……），信息量最低，排最后。</li>
 * </ol>
 * <p>
 * <b>为什么档位判定不引入新字段：</b>{@code cooldown} 与 {@code max} 都是包里已经传过来的，
 * 直接拿来分档<b>不需要改网络包、不需要改注册表、不需要改任何一个读取器</b>。
 * 若将来要更精细的优先级，再考虑往 {@code Info} 里加权重也不迟——但那要动全部注册点，
 * 在收益尚不明确之前不值得。
 * </p>
 * <p>
 * <b>与 HUD 折叠的关系：</b>{@code StackHudOverlay} 在行数超出屏幕时会折叠掉<b>末尾</b>若干行。
 * 本排序保证被折叠掉的一定是最不重要的常驻状态，而不是某个正在倒数的冷却。
 * 这两处改动是配套的。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
public final class StackHudManager {

    /**
     * 一条叠层显示数据。
     *
     * @param serialId 序列号（用于反查元数据）
     * @param count    叠层模式为当前层数；冷却模式为剩余冷却 tick
     * @param max      叠层模式为当前上限；冷却模式为总冷却 tick（<=0 表示不画进度条）
     * @param cooldown true 为冷却倒计时项（显示剩余秒数 + 充能进度条），false 为普通叠层项
     */
    public record Entry(int serialId, int count, int max, boolean cooldown) {
    }

    /**
     * 排序档位：数值越小越靠上。
     * <p>0 = 冷却倒计时，1 = 带进度条的叠层，2 = 只显示数字的常驻状态。详见类注释。</p>
     *
     * @param entry 显示项
     * @return 档位
     */
    private static int tierOf(Entry entry) {
        if (entry.cooldown()) {
            return 0;
        }
        return entry.max() > 0 ? 1 : 2;
    }

    /**
     * 显示顺序比较器：先按档位，同档按 serialId。
     * <p>做成常量而非每次 {@code accept} 现建：比较器是无状态的，
     * 而 {@code accept} 每 3 tick 就会被调一次。</p>
     */
    private static final Comparator<Entry> ORDER =
            Comparator.comparingInt(StackHudManager::tierOf)
                    .thenComparingInt(Entry::serialId);

    /** 当前要显示的叠层列表（volatile：网络线程写、渲染线程读） */
    private static volatile List<Entry> entries = Collections.emptyList();

    private StackHudManager() {
    }

    /**
     * 用服务端推送的全量快照刷新列表。
     *
     * @param stacks serialId -> (层数 / 剩余冷却, 上限 / 总冷却, 是否冷却) 的映射（空映射表示清空 HUD）
     */
    public static void accept(Map<Integer, StackDisplayRegistry.Stacks> stacks) {
        if (stacks.isEmpty()) {
            entries = Collections.emptyList();
            return;
        }
        List<Entry> list = new ArrayList<>(stacks.size());
        for (Map.Entry<Integer, StackDisplayRegistry.Stacks> e : stacks.entrySet()) {
            StackDisplayRegistry.Stacks s = e.getValue();
            list.add(new Entry(e.getKey(), s.count(), s.max(), s.cooldown()));
        }
        // v1.1：先按变化性分档、同档按 serialId。
        // 同档内仍按 serialId 是必须的——只按档位排序的话，同档项的相对顺序取决于
        // HashMap 的遍历顺序，会每次轮询都变，HUD 上的行就会互相换位。
        list.sort(ORDER);
        entries = list;
    }

    /**
     * @return 当前叠层列表（只读）
     */
    public static List<Entry> getEntries() {
        return entries;
    }

    /**
     * 清空（离开世界等场景调用，避免残留）。
     */
    public static void clear() {
        entries = Collections.emptyList();
    }
}
