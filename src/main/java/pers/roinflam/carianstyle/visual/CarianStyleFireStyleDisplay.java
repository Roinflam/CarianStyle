package pers.roinflam.carianstyle.visual;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireDevoured;
import pers.roinflam.carianstyle.enchantment.EnchantmentFireGivesPower;
import pers.roinflam.carianstyle.enchantment.EnchantmentHealingByFire;
import pers.roinflam.carianstyle.enchantment.EnchantmentShelterOfFire;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentGiantFlame;
import pers.roinflam.carianstyle.utils.Reference;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
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
 * @author RoinFlam
 * @version 1.1
 */
@Deprecated
public final class CarianStyleFireStyleDisplay {
    public static final int FIRE_STYLE_BADGE = 24;
    private static final String FIRE_STYLE_NAME_KEY = "carianstyle.hud.fire_style";
    private static final int FIRE_STYLE_COLOR = 0xFF8A2A;
    private CarianStyleFireStyleDisplay() {
    }
}

