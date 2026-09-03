package pers.roinflam.carianstyle.visual;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 装备附魔快照上下文（服务端，叠层 HUD 轮询专用）。
 * <p>
 * <b>解决的问题：</b>{@code StackDisplayManager} 每 3 tick 对每个在线玩家跑一遍全部
 * {@link StackDisplayRegistry.ContextualStackProvider}（当前 22 个）。优化前每个读取器各自调用
 * {@code EnchantmentHelper.getItemEnchantmentLevel(ench, stack)} 做门控判断，而该方法内部会
 * <b>逐条遍历物品的附魔 NBT 列表、并为每条 {@code ResourceLocation.tryParse} 一次</b>——
 * 也就是说，同一件武器 / 同一套护甲的 NBT 在一轮里被反复解析几十遍，产生大量临时
 * {@link net.minecraft.resources.ResourceLocation} 与字符串解析开销。
 * 护甲类读取器还要额外套一层「遍历 4 件护甲」的循环，倍数进一步放大。
 * </p>
 * <p>
 * <b>本类的作用：</b>在每个玩家每轮轮询开始时<b>只解析一次</b>装备 NBT
 * （主手 1 件 + 护甲 4 件，共 5 次 {@link EnchantmentHelper#getEnchantments(ItemStack)}），
 * 构建三张附魔等级表；随后所有读取器的门控判断全部降为 {@link HashMap} 的 O(1) 查表。
 * 单玩家单轮的 NBT 遍历次数由数十次降为至多 5 次。
 * </p>
 *
 * <h3>三张表分别对应三种门控口径</h3>
 * <ul>
 *     <li>{@link #mainHandLevel} —— 主手等级。武器类附魔（居合、碎星、奉剑……）用；</li>
 *     <li>{@link #armorMaxLevel} —— 四件护甲中的<b>最高</b>等级。
 *         「任一护甲带该附魔即生效」的门控用；</li>
 *     <li>{@link #armorSumLevel} —— 四件护甲的等级<b>之和</b>（<b>v1.1 新增</b>）。</li>
 * </ul>
 *
 * <h4>v1.1 为什么必须补上「等级之和」这一口径</h4>
 * <p>
 * 本模组有相当一批护甲附魔的效果强度是按<b>四件护甲的等级累加</b>算的，
 * 它们内部长这样：
 * </p>
 * <pre>
 * int totalLevel = 0;
 * for (ItemStack armor : entity.getArmorSlots()) {
 *     totalLevel += EnchantmentHelper.getItemEnchantmentLevel(ench, armor);
 * }
 * </pre>
 * <p>
 * 黑焰庇护、先祖之角、诺克斯之月、快步都是这个写法。对它们而言
 * {@link #armorMaxLevel} 是<b>错的口径</b>——四件各 3 级时实际按 12 级生效，
 * 取最高只会得到 3。HUD 若用最高值去算减伤 / 加速，显示出来的数字与实际效果对不上，
 * 那比不显示更糟。
 * </p>
 * <p>
 * 原类注释里已经写明「若将来新增读取器需要其它口径，在此扩展即可」，本次即是该扩展。
 * <b>三张表共用同一次 NBT 解析</b>，新增这一张的边际成本仅为一个 {@link HashMap}
 * 与每条附魔一次加法；且与最高值表一样<b>懒分配</b>，裸装 / 无附魔护甲的玩家不产生任何对象。
 * </p>
 *
 * <p>
 * <b>性能细节：</b>
 * <ul>
 *     <li>空槽位 / 未附魔槽位用 {@link ItemStack#isEnchanted()} 前置过滤——该方法只检查 NBT
 *         标签是否存在，不做任何反序列化，开销极低；裸装玩家因此不产生任何 Map 分配；</li>
 *     <li>三张表在无内容时共用 {@link Collections#emptyMap()} 单例，不分配空 HashMap；</li>
 *     <li>本对象为一次性使用（构造 → 本轮读取器用完 → 丢弃），生命周期极短，对 GC 友好。</li>
 * </ul>
 * </p>
 * <p>
 * <b>行为等价性说明（重要）：</b>本类用 {@link EnchantmentHelper#getEnchantments(ItemStack)} 读取，
 * 而原实现走的是 {@code getItemEnchantmentLevel}。两者对<b>普通装备</b>的读取结果完全一致；
 * 唯一差异在于前者对「附魔书」会改读 {@code StoredEnchantments} 标签。为保持与原行为一致，
 * 本类用 {@link ItemStack#isEnchanted()}（只看 {@code Enchantments} 标签）做前置过滤，
 * 因此手持 / 穿戴附魔书时同样不会产生任何叠层显示，与优化前表现相同。
 * </p>
 * <p>
 * 仅在服务端主线程构造与读取，无并发问题。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
public final class EquipmentEnchantContext {

    /**
     * 空表单例：槽位无附魔时共用，避免分配空 {@link HashMap}。
     */
    private static final Map<Enchantment, Integer> EMPTY = Collections.emptyMap();

    /**
     * 主手物品上的附魔等级表（附魔 -> 等级）。
     */
    private final Map<Enchantment, Integer> mainHandLevels;

    /**
     * 4 件护甲上的附魔等级表，同名附魔取<b>最高等级</b>（附魔 -> 最高等级）。
     * <p>「任一护甲是否带该附魔」这类门控用它即可，且顺带支持「按等级判断」。</p>
     */
    private final Map<Enchantment, Integer> armorMaxLevels;

    /**
     * 4 件护甲上的附魔等级表，同名附魔取<b>等级之和</b>（附魔 -> 等级和）。
     * <p><b>v1.1 新增。</b>供效果强度按四件累加计算的护甲附魔使用
     * （黑焰庇护、先祖之角、诺克斯之月、快步等），详见类注释。</p>
     */
    private final Map<Enchantment, Integer> armorSumLevels;

    /**
     * 构建某玩家本轮的装备附魔快照。
     * <p>只解析主手与 4 件护甲，共至多 5 次 NBT 反序列化；两张护甲表共用这一次解析。</p>
     *
     * @param player 目标玩家（服务端实体）
     */
    public EquipmentEnchantContext(@Nonnull Player player) {
        this.mainHandLevels = readSlot(player.getMainHandItem());

        // 两张护甲表都懒分配：全身无附魔护甲时不产生任何 Map 对象
        Map<Enchantment, Integer> armorMax = null;
        Map<Enchantment, Integer> armorSum = null;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty() || !stack.isEnchanted()) {
                continue;
            }
            for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet()) {
                if (armorMax == null) {
                    armorMax = new HashMap<>(8);
                    armorSum = new HashMap<>(8);
                }
                armorMax.merge(entry.getKey(), entry.getValue(), Math::max);
                armorSum.merge(entry.getKey(), entry.getValue(), Integer::sum);
            }
        }
        this.armorMaxLevels = (armorMax == null) ? EMPTY : armorMax;
        this.armorSumLevels = (armorSum == null) ? EMPTY : armorSum;
    }

    /**
     * 读取单个槽位的附魔等级表。
     * <p>空槽位 / 未附魔槽位直接返回空表单例，不做反序列化、不分配对象。</p>
     *
     * @param stack 槽位物品
     * @return 附魔等级表；无附魔时为不可变空表
     */
    @Nonnull
    private static Map<Enchantment, Integer> readSlot(@Nonnull ItemStack stack) {
        if (stack.isEmpty() || !stack.isEnchanted()) {
            return EMPTY;
        }
        Map<Enchantment, Integer> levels = EnchantmentHelper.getEnchantments(stack);
        return levels.isEmpty() ? EMPTY : levels;
    }

    /**
     * 取主手物品上指定附魔的等级。
     *
     * @param enchantment 附魔对象；为 {@code null}（如附魔未注册 / 被禁用）时视为未持有
     * @return 等级；主手未带该附魔时为 0
     */
    public int mainHandLevel(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return 0;
        }
        return mainHandLevels.getOrDefault(enchantment, 0);
    }

    /**
     * 取 4 件护甲上指定附魔的最高等级。
     * <p>返回值 &gt; 0 即等价于原实现中「任一护甲槽带有该附魔」的判定。</p>
     *
     * @param enchantment 附魔对象；为 {@code null}（如附魔未注册 / 被禁用）时视为未持有
     * @return 最高等级；全部护甲均未带该附魔时为 0
     */
    public int armorMaxLevel(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return 0;
        }
        return armorMaxLevels.getOrDefault(enchantment, 0);
    }

    /**
     * 取 4 件护甲上指定附魔的<b>等级之和</b>（v1.1 新增）。
     * <p>
     * 与附魔内部「遍历 {@code getArmorSlots()} 逐件累加」的写法口径完全一致。
     * <b>凡是效果强度按累加等级计算的护甲附魔，HUD 必须用本方法而非
     * {@link #armorMaxLevel}</b>，否则显示的数值与实际生效的不是一回事。
     * </p>
     * <p>
     * <b>本方法不做等级上限钳制。</b>各附魔的 {@code ConfigLoader.levelLimit} 钳制口径不一致
     * （有的钳到 10、有的完全不钳），故一律由调用方按对应附魔源码自行处理。
     * </p>
     *
     * @param enchantment 附魔对象；为 {@code null}（如附魔未注册 / 被禁用）时视为未持有
     * @return 四件护甲的等级之和；全部护甲均未带该附魔时为 0
     */
    public int armorSumLevel(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return 0;
        }
        return armorSumLevels.getOrDefault(enchantment, 0);
    }

    /**
     * 主手是否带有指定附魔。
     *
     * @param enchantment 附魔对象；可为 {@code null}
     * @return 带有返回 true
     */
    public boolean mainHandHas(@Nullable Enchantment enchantment) {
        return mainHandLevel(enchantment) > 0;
    }

    /**
     * 任一护甲是否带有指定附魔。
     *
     * @param enchantment 附魔对象；可为 {@code null}
     * @return 带有返回 true
     */
    public boolean armorHas(@Nullable Enchantment enchantment) {
        return armorMaxLevel(enchantment) > 0;
    }
}
