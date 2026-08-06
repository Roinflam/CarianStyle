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
 *     <li>叠层读取器 {@link ContextualStackProvider} —— 服务端轮询时取当前 {@link Stacks}（层数 + 当前上限）。</li>
 * </ul>
 * <b>上限改为随玩家动态上报：</b>部分附魔（如米莉森义肢）上限随附魔等级变化（等级×16），
 * 故上限不再放进静态 {@link Info}，而由读取器每次返回；网络包会把上限一并下发，
 * 客户端据此决定是否画进度条（上限>0 才画）。
 * <p>
 * 本类在客户端与服务端都会被加载并填充（静态注册），客户端凭 serialId 反查 {@link Info}，
 * 因此网络包里传 (serialId, 层数, 上限, 是否冷却) 即可。
 * <p>
 * <b>v2 性能优化（叠层 HUD 轮询链路，行为与显示完全不变）：</b>
 * 新增 {@link ContextualStackProvider}——读取器额外接收一个 {@link EquipmentEnchantContext}，
 * 即「本玩家本轮的装备附魔快照」。{@code StackDisplayManager} 每轮为每个玩家只构建一次快照，
 * 全部读取器共用，把原先「每个读取器各自解析装备 NBT」的重复开销收敛为每玩家每轮至多 5 次
 * NBT 反序列化（详见 {@link EquipmentEnchantContext} 的类注释）。
 * <p>
 * <b>向后兼容：</b>原有的 {@link StackProvider}（只接收 {@link Player}）与对应的
 * {@link #register(int, Info, StackProvider)} 重载<b>全部保留</b>，旧写法无需改动即可继续工作；
 * 内部会自动包装为上下文版。同时 {@link ContextualStackProvider} 提供了默认的
 * {@code getStacks(Player)} 重载（自行构建一次性快照），因此形如
 * {@code entry.provider().getStacks(player)} 的既有调用点同样不受影响。
 *
 * @author FlameForge
 * @version 2
 */
public final class StackDisplayRegistry {

    /**
     * 叠层读取结果：当前层数 + 当前上限 + 是否为冷却倒计时项。
     * <p>
     * <b>两种显示模式（由 {@code cooldown} 区分，复用同一套同步与渲染管线）：</b>
     * <ul>
     *     <li>{@code cooldown=false}（叠层模式，默认）：{@code count} 为当前层数 / 数值，
     *         {@code max} 为上限；HUD 显示「×count」+ 进度条（填充 = count/max），满层燃烧。</li>
     *     <li>{@code cooldown=true}（冷却模式）：{@code count} 为<b>剩余冷却 tick</b>，
     *         {@code max} 为<b>总冷却 tick</b>；HUD 显示「剩余秒数 s」+ 充能进度条
     *         （填充 = (max-count)/max，从空到满表示冷却恢复），冷却结束（count 归 0）即消失，
     *         不触发满层燃烧。</li>
     * </ul>
     *
     * @param count    叠层模式为层数 / 数值；冷却模式为剩余冷却 tick（&lt;=0 表示无、不显示）
     * @param max      叠层模式为上限；冷却模式为总冷却 tick（&lt;=0 时 HUD 不画进度条）
     * @param cooldown true 为冷却倒计时项，false 为普通叠层项
     */
    public record Stacks(int count, int max, boolean cooldown) {

        /** 表示“当前没有该叠层 / 冷却”的常量。 */
        public static final Stacks NONE = new Stacks(0, 0, false);

        /**
         * 兼容旧调用的双参构造（叠层模式，{@code cooldown=false}）。
         * <p>现有十余处 {@code new Stacks(count, max)} 调用无需改动，仍创建普通叠层项；
         * 冷却倒计时项改用三参构造、传 {@code cooldown=true}。</p>
         *
         * @param count 当前层数 / 数值
         * @param max   当前上限
         */
        public Stacks(int count, int max) {
            this(count, max, false);
        }
    }

    /**
     * 叠层读取器（<b>旧版接口，保留以兼容既有调用</b>）：给定玩家返回其当前 {@link Stacks}。仅服务端调用。
     * <p>新代码建议改用 {@link ContextualStackProvider}，以复用每轮只构建一次的装备附魔快照，
     * 避免重复解析装备 NBT。</p>
     */
    @FunctionalInterface
    public interface StackProvider {
        /**
         * @param player 目标玩家（服务端实体）
         * @return 当前层数与上限；层数<=0 表示无（不显示）
         */
        Stacks getStacks(Player player);
    }

    /**
     * 叠层读取器（上下文版，推荐）：给定玩家与其本轮装备附魔快照，返回当前 {@link Stacks}。仅服务端调用。
     * <p>快照由 {@code StackDisplayManager} 每玩家每轮统一构建一次并共享给全部读取器，
     * 读取器内部的装备门控判断应改为查快照（O(1) 查表），不要再自行调用
     * {@code EnchantmentHelper.getItemEnchantmentLevel} 解析 NBT。</p>
     */
    @FunctionalInterface
    public interface ContextualStackProvider {

        /**
         * @param player 目标玩家（服务端实体）
         * @param ctx    本玩家本轮的装备附魔快照
         * @return 当前层数与上限；层数<=0 表示无（不显示）
         */
        Stacks getStacks(Player player, EquipmentEnchantContext ctx);

        /**
         * 兼容旧调用点的便捷重载：自行构建一次性快照后再取值。
         * <p>该重载<b>不共享快照</b>，仅供偶发的单点查询使用；轮询热路径请务必走
         * {@link #getStacks(Player, EquipmentEnchantContext)} 并复用同一个快照。</p>
         *
         * @param player 目标玩家
         * @return 当前层数与上限
         */
        default Stacks getStacks(Player player) {
            return getStacks(player, new EquipmentEnchantContext(player));
        }
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
     * @param provider 叠层读取器（上下文版；旧版 {@link StackProvider} 注册时会被自动包装）
     */
    public record Entry(int serialId, Info info, ContextualStackProvider provider) {
    }

    /** serialId -> 元数据（双端） */
    private static final Map<Integer, Info> INFO = new HashMap<>();
    /** 全部注册项（服务端轮询用） */
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private StackDisplayRegistry() {
    }

    /**
     * 注册一个叠层显示项（<b>旧版重载，保留以兼容既有调用</b>）。
     * <p>内部会把读取器包装为上下文版（忽略快照参数）。新代码建议改用
     * {@link #register(int, Info, ContextualStackProvider)}。</p>
     *
     * @param serialId 序列号（全局唯一，建议用常量集中管理）
     * @param info     显示元数据
     * @param provider 叠层读取器
     * @throws IllegalArgumentException 当 serialId 重复时
     */
    public static void register(int serialId, Info info, StackProvider provider) {
        register(serialId, info, (player, ctx) -> provider.getStacks(player));
    }

    /**
     * 注册一个叠层显示项（上下文版，推荐）。
     *
     * @param serialId 序列号（全局唯一，建议用常量集中管理）
     * @param info     显示元数据
     * @param provider 叠层读取器（可复用本轮装备附魔快照）
     * @throws IllegalArgumentException 当 serialId 重复时
     */
    public static void register(int serialId, Info info, ContextualStackProvider provider) {
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
