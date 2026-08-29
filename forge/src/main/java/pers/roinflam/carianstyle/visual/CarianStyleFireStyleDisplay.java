package pers.roinflam.carianstyle.visual;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentGiantFlame;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 火焰流派「联动徽标」的 HUD 叠层注册（双端通用）。
 *
 * <h3>这一行要回答什么</h3>
 * <p>
 * 火焰流派由五个附魔构成，它们各自的说明文本都写着「着火时：……」，
 * 也就是说<b>整套流派的绝大部分收益共用同一个开关——「你自己正在燃烧」</b>：
 * </p>
 * <ul>
 *     <li>{@code fire_gives_power}（主手）—— 着火时增伤 7.5%×等级、物理减伤 3.75%×等级；</li>
 *     <li>{@code fire_devoured}（主手）—— 着火时攻击附带范围火焰伤害并点燃周围；</li>
 *     <li>{@code shelter_of_fire}（护甲）—— 着火时减伤 2%×等级、每秒回血 0.1%×等级；</li>
 *     <li>{@code healing_by_fire}（护甲）—— 着火时受击有概率驱散负面并获得吸收盾；</li>
 *     <li>{@code giant_flame}（胸甲）—— 着火时把受到的伤害反弹 50% 给攻击者。</li>
 * </ul>
 * <p>
 * 问题在于：<b>「我现在到底着没着火」这件事在战斗中意外地难判断。</b>
 * 原版只有屏幕边缘那圈火焰贴图，而它在火把、岩浆、夜视、各种粒子特效之间很容易被忽略；
 * 更要命的是玩家<b>完全不知道自己身上这五件装备里有几件正在吃到这个加成</b>——
 * 配了四件火焰装却因为没点着而一件都没生效，屏幕上不会有任何提示。
 * </p>
 * <p>
 * 本行就显示这一个数：<b>当前生效件数 / 已装备件数</b>。
 * 进度条空着就是「你带了这套装但没点着」，满格就是「五件全开，可以打了」。
 * </p>
 *
 * <h3>giant_flame 为什么恒计入生效</h3>
 * <p>
 * 它是五个里唯一<b>不完全依赖着火</b>的：
 * </p>
 * <ul>
 *     <li>按血量比例的减伤（{@code onLivingHurt}）——无条件生效；</li>
 *     <li>免疫火焰伤害并转为治疗（{@code onLivingAttack}）——无条件生效；</li>
 *     <li>只有 50% 伤害反弹（{@code onLivingDamage}）才要求 {@code getRemainingFireTicks() > 0}。</li>
 * </ul>
 * <p>
 * 所以只要穿着它就至少有两项在跑，计入生效是如实的。
 * 这也让徽标在未点燃时读作 {@code 1/5} 而不是 {@code 0/5}——
 * 前者准确表达了「胸甲那件一直在保你，其余四件在等你点火」。
 * </p>
 *
 * <h3>判定用 isOnFire() 而不是 getRemainingFireTicks()</h3>
 * <p>
 * <b>这是本类唯一需要小心的地方。</b>五个附魔在服务端判定的都是
 * {@code getRemainingFireTicks() > 0}，但那个字段<b>不是同步字段</b>——
 * 服务端调用 {@code setSecondsOnFire} 时只会把
 * {@code DATA_SHARED_FLAGS_ID} 的第 0 位（「正在燃烧」布尔量）广播出去，
 * 剩余 tick 数从不下发。
 * </p>
 * <p>
 * 因此客户端读到的 {@code getRemainingFireTicks()} 只反映客户端自己算出来的那份
 * （例如踩进火焰方块时的本地预测），<b>对 {@code fire_gives_power} 这种
 * 服务端点燃的情况恒为 0</b>。用它做判定会让徽标在实际生效时显示未生效。
 * </p>
 * <p>
 * 而 {@code isOnFire()} 读的正是那个同步标志位，服务端每 tick 由
 * {@code setSharedFlagOnFire(remainingFireTicks > 0)} 维护，与附魔的判定条件等价。
 * </p>
 * <p>
 * <b>唯一的偏差：</b>{@code setSharedFlagOnFire} 实际是
 * {@code setSharedFlag(0, onFire || hasVisualFire)}，所以带
 * {@code hasVisualFire} 的实体（少数模组的纯视觉火焰）会让标志位为真而附魔判定为假。
 * 玩家身上出现这种情况极其罕见，且后果只是徽标偏乐观一档，不影响任何机制。
 * </p>
 * <p>
 * <b>如果将来要精确到秒</b>（例如显示「燃烧剩余 7s」），就必须新增一个
 * 同步剩余 tick 的包——那是另一条链路，本次刻意没做，因为
 * {@code fire_gives_power} 每次攻击都会把自己续燃到 10 秒，
 * 只要在输出就几乎恒定满值，秒数的信息量远低于「几件在生效」。
 * </p>
 *
 * <h3>为什么全部信息都在客户端就能算出来</h3>
 * <p>
 * 附魔等级存在 {@code ItemStack} 的 NBT 里、随装备槽位同步给客户端，
 * 燃烧标志位随实体数据同步——<b>两样客户端都有</b>，
 * 因此本行不需要任何新增网络包，也不需要服务端配合。
 * </p>
 *
 * <h3>为什么单开一个类而不是并进 CarianStyleStackDisplays</h3>
 * <p>
 * 与 {@link CarianStyleTimeReversalDisplay} 同理：那个类七百余行、集中登记 22 项，
 * 为加一项而整体重写的风险大于收益。本类用 {@link FMLCommonSetupEvent} 自行注册，
 * {@code StackDisplayRegistry.register} 只是往列表里追加，注册先后不影响任何行为。
 * </p>
 * <p>
 * <b>想统一管理的话</b>：把 {@link #register()} 里那段
 * {@code StackDisplayRegistry.register(...)} 剪进 {@code CarianStyleStackDisplays.init()}、
 * 序列号常量搬过去，再删掉本文件即可，其余代码一行不用动。
 * </p>
 *
 * <h3>v1.1 修复：三个私有辅助方法的形参类型收窄</h3>
 * <p>
 * {@link #armorEquipped} / {@link #mainHandEquipped} / {@link #resolve} 原先把附魔类
 * 声明为 {@code Class<?>}，而 {@link EnchantmentRegistry#getEnchantmentByClass} 的形参是
 * {@code Class<? extends EnchantmentBase>}。{@code Class<?>} 经通配符捕获后得到的是一个
 * 「继承自 Object 的未知类型」，编译器无法证明它满足 {@code EnchantmentBase} 的上界，
 * 于是在 {@link #resolve} 内部报「不兼容的类型: Class&lt;CAP#1&gt; 无法转换为
 * Class&lt;? extends EnchantmentBase&gt;」。
 * </p>
 * <p>
 * 修复方式是把三处形参统一改为 {@code Class<? extends EnchantmentBase>}。
 * 调用点传入的本来就是五个具体附魔类（它们都继承自 {@code EnchantmentBase}），
 * <b>无需任何改动</b>；同时这个签名也把「只能传附魔类」这一约束前移到了编译期，
 * 比原先的 {@code Class<?>} 更不容易误用。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CarianStyleFireStyleDisplay {

    /**
     * 「火焰流派」徽标的叠层序列号。
     * <p>{@code CarianStyleStackDisplays} 已用 1~22，
     * {@link CarianStyleTimeReversalDisplay} 用了 23，故本项取 24。
     * 新增其它显示项请从 25 起。</p>
     */
    public static final int FIRE_STYLE_BADGE = 24;

    /**
     * 名称翻译键。
     * <p>需要在 {@code zh_cn.json} 中补上，否则 HUD 会直接显示这个原始键名。</p>
     */
    private static final String FIRE_STYLE_NAME_KEY = "carianstyle.hud.fire_style";

    /**
     * 主题色（火焰橙）。
     * <p>与本模组既有的火焰语汇一致；比癫火黄 {@code 0xFFE020} 深、
     * 比出血红 {@code 0xE0202F} 暖，在 HUD 列表里一眼能认出是火系那一行。</p>
     */
    private static final int FIRE_STYLE_COLOR = 0xFF8A2A;

    /** 防止重复注册（{@code register} 抛 IllegalArgumentException 会打断整个 setup） */
    private static boolean initialized = false;

    private CarianStyleFireStyleDisplay() {
    }

    /**
     * 公共初始化阶段注册显示项。
     * <p>
     * 用 {@code enqueueWork} 而非直接注册：{@link FMLCommonSetupEvent} 的监听器
     * 可能并行执行，而 {@code StackDisplayRegistry} 内部是普通集合，
     * 并发写入会损坏结构。{@code enqueueWork} 保证在主线程串行执行。
     * </p>
     *
     * @param event 公共初始化事件
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(CarianStyleFireStyleDisplay::register);
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
                FIRE_STYLE_BADGE,
                new StackDisplayRegistry.Info(FIRE_STYLE_NAME_KEY, FIRE_STYLE_COLOR),
                CarianStyleFireStyleDisplay::computeStacks);
    }

    /**
     * 统计当前「已装备 / 正在生效」的火焰流派件数。
     * <p>
     * 一件都没装备时返回 {@link StackDisplayRegistry.Stacks#NONE}——
     * 没走火焰流派的玩家不该看到这一行。
     * </p>
     * <p>
     * <b>开销：</b>每次 HUD 轮询做 5 次 {@code getEnchantmentByClass}（HashMap 查询）
     * 加至多 5 次装备槽扫描。HUD 轮询频率远低于每 tick，
     * 与 {@code CarianStyleStackDisplays} 里既有的十余项同量级，无需额外缓存。
     * </p>
     *
     * @param player 本地玩家
     * @param ctx    装备附魔上下文
     * @return 生效件数 / 已装备件数；未装备任何一件时为 {@code NONE}
     */
    private static StackDisplayRegistry.Stacks computeStacks(Player player,
                                                             EquipmentEnchantContext ctx) {
        // 用同步标志位而非剩余 tick，理由见类注释「判定用 isOnFire()」小节
        boolean burning = player.isOnFire();

        int equipped = 0;
        int active = 0;

        // ===== 胸甲：巨人火焰 =====
        // 五者中唯一部分效果不依赖着火（减伤 + 火免疫转治疗），故只要装备即计入生效
        if (armorEquipped(ctx, EnchantmentGiantFlame.class)) {
            equipped++;
            active++;
        }

        // ===== 护甲四件套：火焰庇护 / 火焰疗愈 =====
        if (armorEquipped(ctx, EnchantmentShelterOfFire.class)) {
            equipped++;
            if (burning) {
                active++;
            }
        }
        if (armorEquipped(ctx, EnchantmentHealingByFire.class)) {
            equipped++;
            if (burning) {
                active++;
            }
        }

        // ===== 主手：火焰赐力 / 火焰吞噬 =====
        if (mainHandEquipped(ctx, EnchantmentFireGivesPower.class)) {
            equipped++;
            if (burning) {
                active++;
            }
        }
        if (mainHandEquipped(ctx, EnchantmentFireDevoured.class)) {
            equipped++;
            if (burning) {
                active++;
            }
        }

        if (equipped <= 0) {
            return StackDisplayRegistry.Stacks.NONE;
        }
        return new StackDisplayRegistry.Stacks(active, equipped);
    }

    /**
     * 判断护甲上是否带有指定附魔。
     * <p>
     * 走 {@code armorMaxLevel} 取四个槽位中的最高等级再判正，
     * 而不是求和——本行只关心「有没有」，与等级无关。
     * </p>
     * <p>
     * <b>v1.1：</b>形参由 {@code Class<?>} 收窄为 {@code Class<? extends EnchantmentBase>}，
     * 以匹配 {@link EnchantmentRegistry#getEnchantmentByClass} 的签名（详见类注释）。
     * </p>
     *
     * @param ctx          装备附魔上下文
     * @param enchantClass 附魔实现类
     * @return 是否装备
     */
    private static boolean armorEquipped(EquipmentEnchantContext ctx,
                                         Class<? extends EnchantmentBase> enchantClass) {
        Enchantment ench = resolve(enchantClass);
        // 附魔可能被 uninstallEnchantment 配置禁用而未注册，此时恒为未装备
        return ench != null && ctx.armorMaxLevel(ench) > 0;
    }

    /**
     * 判断主手武器上是否带有指定附魔。
     * <p><b>v1.1：</b>形参类型同 {@link #armorEquipped}。</p>
     *
     * @param ctx          装备附魔上下文
     * @param enchantClass 附魔实现类
     * @return 是否装备
     */
    private static boolean mainHandEquipped(EquipmentEnchantContext ctx,
                                            Class<? extends EnchantmentBase> enchantClass) {
        Enchantment ench = resolve(enchantClass);
        return ench != null && ctx.mainHandLevel(ench) > 0;
    }

    /**
     * 按实现类解析附魔对象。
     * <p>
     * <b>v1.1：</b>形参由 {@code Class<?>} 收窄为 {@code Class<? extends EnchantmentBase>}。
     * 原先的通配符捕获无法满足 {@link EnchantmentRegistry#getEnchantmentByClass} 的
     * {@code EnchantmentBase} 上界，导致编译失败（详见类注释的「v1.1 修复」小节）。
     * </p>
     *
     * @param enchantClass 附魔实现类
     * @return 附魔对象；未注册（被配置禁用）时返回 {@code null}
     */
    private static Enchantment resolve(Class<? extends EnchantmentBase> enchantClass) {
        return EnchantmentRegistry.getEnchantmentByClass(enchantClass);
    }
}
