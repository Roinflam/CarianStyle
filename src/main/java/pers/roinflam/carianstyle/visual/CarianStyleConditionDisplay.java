package pers.roinflam.carianstyle.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameShelter;
import pers.roinflam.carianstyle.enchantment.EnchantmentConcealingVeil;
import pers.roinflam.carianstyle.enchantment.EnchantmentEatShit;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.DayNightUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 「条件生效 / 冷却中」类附魔的 HUD 叠层注册（双端通用）。
 *
 * <h3>这一批要回答什么</h3>
 * <p>
 * 这里登记的八项，此前<b>一点视觉反馈都没有</b>——既没有世界特效，也没有 HUD。
 * 它们的共同点是：<b>效果确实在变，但变化本身完全不可见</b>。
 * </p>
 * <ul>
 *     <li><b>黑焰庇护</b>——物理减伤按四件护甲的等级<b>累加</b>算，
 *         玩家自己算不出来穿这四件到底减了多少；</li>
 *     <li><b>隐匿面纱</b>——攻击 / 受击后 3 秒内潜行也不会隐身。
 *         这 3 秒是<b>纯黑箱</b>：玩家蹲下去没隐身，完全不知道是还没到时候、还是压根没生效；</li>
 *     <li><b>吃屎</b>——中招后治疗被砍到 25%，而<b>屏幕上没有任何 debuff 图标</b>
 *         （它不是原版药水效果，是 {@code EnchantmentDataManager} 里的一条冷却记录）。
 *         玩家只会觉得「我怎么喝了药还是不回血」；</li>
 *     <li><b>快步</b>——速度等级 = 已损失生命百分比 ÷ 5 × 护甲累加等级，
 *         是本模组少数<b>越打越强的数值</b>，却只能靠体感；</li>
 *     <li><b>岩石剑</b>——攻击后获得 200 tick 的增益窗口，窗口本身不可见；</li>
 *     <li><b>诺克斯之月 / 暗弃子</b>——都只在<b>夜晚</b>生效，白天完全是废附魔，
 *         而玩家在洞里、在下界时根本判断不了地表是白天还是夜晚；</li>
 *     <li><b>隐身状态</b>——三个附魔都会给 {@code STEALTH}，
 *         而这个状态<b>第一人称下自己看不见</b>，偏偏刺客赌局的爆发伤害完全依赖它。</li>
 * </ul>
 *
 * <h3>序列号从 32 起</h3>
 * <p>
 * {@code CarianStyleStackDisplays} 已用 1~22，{@code CarianStyleTimeReversalDisplay} 用 23，
 * {@code CarianStyleFireStyleDisplay} 用 24，{@link CarianStyleCombatStateDisplay} 用 25~31。
 * 本类占 32~39，新增其它显示项请从 40 起。
 * </p>
 *
 * <h3>为什么单开一个类</h3>
 * <p>
 * 与 {@link CarianStyleCombatStateDisplay} 同理：{@code CarianStyleStackDisplays} 七百余行、
 * 集中登记 22 项，为加八项而整体重写的风险大于收益。本类用 {@link FMLCommonSetupEvent}
 * 自行注册，{@code StackDisplayRegistry.register} 只是往列表里追加，注册先后不影响任何行为。
 * </p>
 *
 * <h3>护甲类一律走「等级之和」，不是「最高等级」</h3>
 * <p>
 * 本批里黑焰庇护、快步、诺克斯之月三项的效果强度都是<b>四件护甲等级累加</b>算的。
 * {@link EquipmentEnchantContext#armorMaxLevel} 对它们是<b>错的口径</b>——
 * 四件各 3 级时实际按 12 级生效，取最高只会得到 3，HUD 会显示一个远小于实际的数字。
 * 故这三项统一使用 v1.1 新增的 {@link EquipmentEnchantContext#armorSumLevel}。
 * </p>
 *
 * <h3>为什么这批大多不画进度条（max = 0）</h3>
 * <p>
 * 只有隐匿面纱一项带进度条（它是真正的冷却，有明确的总时长 60 tick）。
 * 其余各项要么<b>没有可靠的总量</b>（吃屎的 debuff 时长由<b>攻击者</b>的等级决定，
 * 受击方无从得知），要么<b>条件在很长时间里恒为真</b>（黑焰庇护只要穿着就一直生效）。
 * 给后者配进度条并让上限等于当前值，会让它常年满格并持续触发满层燃烧，
 * 变成一个一直在闪的视野污染源——{@link CarianStyleCombatStateDisplay} 里已经踩过这个坑。
 * </p>
 *
 * <h3>刻意没做的五项，以及原因</h3>
 * <ul>
 *     <li><b>剑舞</b>——它读的是 {@code player.getAttackStrengthScale}，也就是
 *         <b>原版攻击冷却</b>，而原版<b>已经在准星下画了攻击力度条</b>。
 *         再开一行 HUD 是同一信息的第二份拷贝；而且它每 tick 都在变、
 *         满值时还会触发满层燃烧，等于每挥一次刀就闪一下；</li>
 *     <li><b>熔炉之羽</b>——它给的是<b>原版</b>的速度 / 跳跃提升，
 *         与其它来源的同名效果<b>无法区分</b>。屏幕右侧本来就有原版药水图标，
 *         这里再显示一行只会重复，而且分不清是不是熔炉之羽给的；</li>
 *     <li><b>卡利亚方阵</b>——延迟发射的状态锁在 {@code SynchronizationTask} 内部，
 *         没有任何可查询的外部记录。硬要做就得改附魔往 {@code EnchantmentDataManager}
 *         写状态，那已经超出「加 HUD」的范围了；</li>
 *     <li><b>先祖之魂 / 先祖之角</b>——两者都是「穿着就恒定生效」的魔法减伤，
 *         数值不随任何条件变化。这种情况下 HUD 显示的其实是
 *         「你装备了这件护甲」，玩家打开背包就能看到，行数却是实打实占掉的。</li>
 * </ul>
 *
 * <h3>口径全部按附魔源码核对，不按语言文件</h3>
 * <p>
 * 语言文件与实现有出入的地方一律以实现为准，逐条标在对应读取器的注释里。
 * HUD 的唯一价值就是<b>如实反映当前到底生效了什么</b>，跟着描述走而与代码不符，
 * 比不显示更有害。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class CarianStyleConditionDisplay {
    public static final int BLACK_FLAME_SHELTER = 32;
    public static final int CONCEALING_VEIL_BATTLE = 33;
    public static final int EAT_SHIT_DEBUFF = 34;
    public static final int QUICKSTEP = 35;
    public static final int CRAGBLADE = 36;
    public static final int MOON_OF_NOXTURA = 37;
    public static final int DARK_ABANDONED_CHILD = 38;
    public static final int STEALTH_ACTIVE = 39;
    private static final String BLACK_FLAME_SHELTER_ID = "black_flame_shelter";
    private static final String CONCEALING_VEIL_ID = "concealing_veil";
    private static final String QUICKSTEP_ID = "quickstep";
    private static final String CRAGBLADE_ID = "cragblade";
    private static final String MOON_OF_NOXTURA_ID = "moon_of_noxtura";
    private static final String DARK_ABANDONED_CHILD_ID = "dark_abandoned_child";
    private static final String BLACK_FLAME_SHELTER_NAME_KEY = "enchantment.carianstyle.black_flame_shelter";
    private static final String CONCEALING_VEIL_NAME_KEY = "enchantment.carianstyle.concealing_veil";
    private static final String QUICKSTEP_NAME_KEY = "enchantment.carianstyle.quickstep";
    private static final String CRAGBLADE_NAME_KEY = "enchantment.carianstyle.cragblade";
    private static final String MOON_OF_NOXTURA_NAME_KEY = "enchantment.carianstyle.moon_of_noxtura";
    private static final String DARK_ABANDONED_CHILD_NAME_KEY = "enchantment.carianstyle.dark_abandoned_child";
    private static final String EAT_SHIT_NAME_KEY = "carianstyle.hud.eat_shit_debuff";
    private static final String STEALTH_NAME_KEY = "carianstyle.hud.stealth_active";
    private static final int BLACK_FLAME_SHELTER_COLOR = 0x5A3A7A;
    private static final int CONCEALING_VEIL_COLOR = 0x6A7A96;
    private static final int EAT_SHIT_COLOR = 0x8A7A2A;
    private static final int QUICKSTEP_COLOR = 0x4AC0B0;
    private static final int CRAGBLADE_COLOR = 0x9A8468;
    private static final int MOON_OF_NOXTURA_COLOR = 0x4A5AA0;
    private static final int DARK_ABANDONED_CHILD_COLOR = 0x6A4A8A;
    private static final int STEALTH_COLOR = 0x8AA0A8;
    private static final int BLACK_FLAME_SHELTER_PERMILLE_PER_LEVEL = 125;
    private static final int ARMOR_TOTAL_LEVEL_CAP = 10;
    private static final int BLACK_FLAME_SHELTER_MAX_LEVEL = EnchantmentBlackFlameShelter.MAX_EFFECTIVE_LEVEL;
    private static final int QUICKSTEP_HEALTH_STEP_PERCENT = 5;
    private static final int DARK_ABANDONED_CHILD_PERCENT = 10;
    private static final Map<String, Enchantment> ENCHANTMENT_ID_CACHE = new ConcurrentHashMap<>();
    private CarianStyleConditionDisplay() {
    }
}

