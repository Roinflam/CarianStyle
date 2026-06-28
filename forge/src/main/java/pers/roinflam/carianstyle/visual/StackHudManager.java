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
 * @author FlameForge
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
        // 按 serialId 排序，保证多附魔同时显示时顺序稳定（不会每帧跳动）
        list.sort(Comparator.comparingInt(Entry::serialId));
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
