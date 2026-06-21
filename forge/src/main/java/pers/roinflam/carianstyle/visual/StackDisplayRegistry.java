package pers.roinflam.carianstyle.visual;

import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 叠层显示注册表（双端通用）。
 * <p>
 * 负责登记“哪些附魔需要在 HUD 上显示叠层”，以及每个附魔的：
 * <ul>
 *     <li>序列号 serialId —— 网络传输用的紧凑标识（取代字符串，省带宽）；</li>
 *     <li>显示元数据 {@link Info} —— 名称翻译键、颜色（客户端 HUD 渲染用，静态、无需同步）；</li>
 *     <li>叠层读取器 {@link StackProvider} —— 服务端轮询时取当前 {@link Stacks}（层数 + 当前上限）。</li>
 * </ul>
 * <b>上限改为随玩家动态上报：</b>部分附魔（如米莉森义肢）上限随附魔等级变化（等级×16），
 * 故上限不再放进静态 {@link Info}，而由读取器每次返回；网络包会把上限一并下发，
 * 客户端据此决定是否画进度条（上限>0 才画）。
 * <p>
 * 本类在客户端与服务端都会被加载并填充（静态注册），客户端凭 serialId 反查 {@link Info}，
 * 因此网络包里传 (serialId, 层数, 上限) 即可。
 *
 * @author FlameForge
 */
public final class StackDisplayRegistry {

    /**
     * 叠层读取结果：当前层数 + 当前上限。
     *
     * @param count 当前层数（<=0 表示当前没有该叠层，不显示）
     * @param max   当前上限（<=0 表示无固定上限，HUD 只显示数字不画进度条）
     */
    public record Stacks(int count, int max) {
        /** 表示“当前没有该叠层”的常量。 */
        public static final Stacks NONE = new Stacks(0, 0);
    }

    /** 叠层读取器：给定玩家返回其当前 {@link Stacks}。仅服务端调用。 */
    @FunctionalInterface
    public interface StackProvider {
        /**
         * @param player 目标玩家（服务端实体）
         * @return 当前层数与上限；层数<=0 表示无（不显示）
         */
        Stacks getStacks(Player player);
    }

    /**
     * 叠层显示元数据（客户端渲染用，静态、无需同步）。
     *
     * @param nameKey 名称翻译键，例如 "enchantment.carianstyle.dragoncrest_greatshield"
     * @param color   主题色（0xRRGGBB，不含 alpha），用于强调色与进度条
     */
    public record Info(String nameKey, int color) {
    }

    /**
     * 单条注册项。
     *
     * @param serialId 序列号
     * @param info     显示元数据
     * @param provider 叠层读取器
     */
    public record Entry(int serialId, Info info, StackProvider provider) {
    }

    /** serialId -> 元数据（双端） */
    private static final Map<Integer, Info> INFO = new HashMap<>();
    /** 全部注册项（服务端轮询用） */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private StackDisplayRegistry() {
    }

    /**
     * 注册一个叠层显示项。
     *
     * @param serialId 序列号（全局唯一，建议用常量集中管理）
     * @param info     显示元数据
     * @param provider 叠层读取器
     * @throws IllegalArgumentException 当 serialId 重复时
     */
    public static void register(int serialId, Info info, StackProvider provider) {
        if (INFO.containsKey(serialId)) {
            throw new IllegalArgumentException("叠层显示 serialId 重复: " + serialId);
        }
        INFO.put(serialId, info);
        ENTRIES.add(new Entry(serialId, info, provider));
    }

    /**
     * @return 全部注册项（只读用途，请勿修改）
     */
    public static List<Entry> getEntries() {
        return ENTRIES;
    }

    /**
     * 按序列号取显示元数据。
     *
     * @param serialId 序列号
     * @return 元数据；不存在时返回 null
     */
    public static Info getInfo(int serialId) {
        return INFO.get(serialId);
    }
}
