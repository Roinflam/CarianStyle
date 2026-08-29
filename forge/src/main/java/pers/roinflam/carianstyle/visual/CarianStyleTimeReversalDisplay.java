package pers.roinflam.carianstyle.visual;

import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 时间逆转「储存伤害」的 HUD 叠层注册（双端通用）。
 *
 * <h3>它显示什么，为什么值得单独占一行</h3>
 * <p>
 * {@code CarianStyleStackDisplays} 里已有一项时间逆转的<b>冷却倒计时</b>（serialId 18），
 * 但那是「下次什么时候能再用」；本项显示的是逆转<b>正在进行时</b>已经攒了多少伤害。
 * </p>
 * <p>
 * 二者信息完全不同，且都很关键：逆转的收益 = 储存值 × 25% 的治疗 + 全额反弹给攻击者，
 * 也就是说「站着多挨几下」是<b>正收益</b>——这与玩家的全部本能相反。
 * 没有这个数字，玩家会本能地在无敌期间逃跑，白白浪费掉附魔最值钱的部分。
 * 有了它，玩家能看着数字往上跳，主动往人堆里站。
 * </p>
 * <p>
 * 两项会在逆转的 5 秒里<b>同时显示</b>（冷却在触发瞬间就开始走），
 * 因此刻意给了不同的名称与配色：冷却那项是原附魔名 + 金褐，
 * 本项是「回溯储存」+ 苍白金，不会看混。
 * </p>
 *
 * <h3>为什么上限填 0（不画进度条）</h3>
 * <p>
 * 储存伤害<b>没有任何上限</b>——机制上你挨多少就存多少。
 * 而 HUD 的进度条填充比例是 {@code count / max}，硬编一个假上限只会骗人：
 * 条满了玩家会以为「存够了、可以跑了」，实际上继续挨打仍在涨。
 * </p>
 * <p>
 * 故按 {@code StackDisplayRegistry} 既定的约定填 {@code max = 0} —— HUD 只显示数字、
 * 不画进度条，与米凯拉之刃、连击这两项同款处理。
 * </p>
 *
 * <h3>v1.1：门控口径改为 armorMaxLevel</h3>
 * <p>
 * 上一版这里写的是 {@code ctx.armorHas(...)}。改成
 * {@code ctx.armorMaxLevel(...) > 0} 有两个原因：
 * </p>
 * <ul>
 *     <li>与新增的 {@link CarianStyleFireStyleDisplay} 保持同一口径，
 *         两处护甲类门控读起来一致；</li>
 *     <li>{@code armorMaxLevel} 是本项目在设计阶段就确认存在的方法，
 *         而 {@code armorHas} 是否为 {@code EquipmentEnchantContext} 的公开 API
 *         我没有当面核对过。二者语义完全等价（都是「护甲上有没有这个附魔」），
 *         换成确定存在的那个可以排除一处编译风险。</li>
 * </ul>
 * <p>
 * <b>行为完全不变</b>：等级恒为正整数，{@code > 0} 与「有」是同一件事。
 * 顺带把 null 判断显式提到前面，不再依赖上下文方法对 {@code null} 的容忍度。
 * </p>
 *
 * <h3>为什么单开一个类而不是并进 CarianStyleStackDisplays</h3>
 * <p>
 * 那个类有七百余行、集中登记了 22 个显示项，为加一项而整体重写的风险
 * （抄错一个 key、漏掉一个常量）远大于收益。本类用
 * {@link FMLCommonSetupEvent} 自行注册，与那边互不干扰——
 * {@code StackDisplayRegistry.register} 只是往一个列表里追加，
 * 两处注册的先后顺序不影响任何行为，serialId 不冲突即可。
 * </p>
 * <p>
 * <b>如果你更希望统一管理</b>，把 {@link #register()} 里那段
 * {@code StackDisplayRegistry.register(...)} 原样剪进
 * {@code CarianStyleStackDisplays.init()}、再把序列号常量搬过去，然后删掉本文件即可，
 * 其余代码一行都不用动。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CarianStyleTimeReversalDisplay {

    /**
     * 「回溯储存」的叠层序列号。
     * <p>{@code CarianStyleStackDisplays} 已用到 1~22，故本项取 23；
     * {@link CarianStyleFireStyleDisplay} 占 24。新增其它显示项请从 25 起。</p>
     */
    public static final int TIME_REVERSAL_STORED = 23;

    /**
     * 逆转期间累积伤害的数据键（值类型 {@code Float}）。
     * <p>
     * <b>⚠ 必须与 {@code EnchantmentTimeReversal.REVERSAL_DAMAGE_KEY} 逐字一致</b>，
     * 改动那边务必同步改这里，否则 HUD 会静默地永远读不到值（不会报错，只是永远不显示）。
     * </p>
     * <p>
     * 复制一份字符串而不是把那边的常量提为 public，是沿用
     * {@code CarianStyleStackDisplays} 的既有惯例——那边十余个 key 全部是这么做的，
     * 且都在注释里标注了来源。保持一致比引入一种新写法更好维护。
     * </p>
     */
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";

    /**
     * 名称翻译键。
     * <p>需要在 {@code zh_cn.json} 中补上，否则 HUD 会直接显示这个原始键名。</p>
     */
    private static final String STORED_NAME_KEY = "carianstyle.hud.time_reversal_stored";

    /**
     * 主题色（苍白金）。
     * <p>刻意比冷却项的 {@code 0xB8902A} 亮且偏白——二者在逆转期间会同屏显示，
     * 需要一眼分得开（详见类注释）。</p>
     */
    private static final int STORED_COLOR = 0xF0E0B0;

    /** 防止重复注册（{@code register} 抛 IllegalArgumentException 会打断整个 setup） */
    private static boolean initialized = false;

    private CarianStyleTimeReversalDisplay() {
    }

    /**
     * 公共初始化阶段注册显示项。
     * <p>
     * 用 {@code enqueueWork} 而非直接注册：{@link FMLCommonSetupEvent} 的监听器
     * 可能并行执行，而 {@code StackDisplayRegistry} 内部是普通 {@code ArrayList} /
     * {@code HashMap}，并发写入会损坏结构。{@code enqueueWork} 保证在主线程串行执行。
     * </p>
     *
     * @param event 公共初始化事件
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CarianStyleTimeReversalDisplay::register);
    }

    /**
     * 实际注册逻辑。重复调用安全。
     */
    private static void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        StackDisplayRegistry.register(
                TIME_REVERSAL_STORED,
                new StackDisplayRegistry.Info(STORED_NAME_KEY, STORED_COLOR),
                (player, ctx) -> {
                    // 门控：护甲上没带时间逆转就不显示。
                    // 用护甲口径而非主手 —— 时间逆转是 ARMOR_CHEST 附魔，
                    // 拿在手上不生效，视觉上也不该显示
                    Enchantment timeReversal = resolveTimeReversal();
                    if (timeReversal == null || ctx.armorMaxLevel(timeReversal) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    Float stored = EnchantmentDataManager.getData(REVERSAL_DAMAGE_KEY, player.getUUID());
                    if (stored == null || stored <= 0f) {
                        // 未处于逆转状态、或刚触发还没挨打：不显示
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    // max 填 0：储存伤害无上限，画进度条会骗人（详见类注释）
                    return new StackDisplayRegistry.Stacks(Math.round(stored), 0);
                });
    }

    /**
     * 解析时间逆转附魔对象。
     * <p>
     * 走 {@link EnchantmentRegistry#getEnchantmentByClass}（内部是 HashMap 查询，
     * 本身已足够廉价），与 {@code CarianStyleStackDisplays} 中按类解析的写法一致，
     * 无需像按 id 解析那样额外加缓存。
     * </p>
     *
     * @return 附魔对象；未注册（如被 {@code uninstallEnchantment} 配置禁用）时返回 null，
     *         此时本项不显示
     */
    private static Enchantment resolveTimeReversal() {
        return EnchantmentRegistry.getEnchantmentByClass(EnchantmentTimeReversal.class);
    }
}
