package pers.roinflam.carianstyle.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentWarrior;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.utils.util.DayNightUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 「战斗状态」类附魔的 HUD 叠层注册（双端通用）。
 *
 * <h3>这一批要回答什么</h3>
 * <p>
 * 这里登记的七项，对应的都是<b>条件生效、但条件本身不可见</b>的附魔。
 * 玩家能看到伤害数字变了，却不知道是哪一条在起作用、离失效还有多远：
 * </p>
 * <ul>
 *     <li><b>誓复仇</b>——伤害随周围生物数量线性上涨，而「周围有几个」在混战中根本数不清；</li>
 *     <li><b>战士</b>——受到的伤害有一半变成 3 秒内的持续流失，
 *         玩家看到血条在没人打自己的时候还在掉，却不知道<b>还要掉多少才停</b>；</li>
 *     <li><b>碎星</b>——以半血为界在增伤档和减伤档之间切换，还要叠一层昼夜；</li>
 *     <li><b>奉剑 / 红羽枝剑 / 蓝羽枝剑</b>——都靠一条血量阈值开关，
 *         掉一滴血或者回一滴血就切换，而那一刻屏幕上没有任何提示。</li>
 * </ul>
 *
 * <h3>序列号从 25 起</h3>
 * <p>
 * {@code CarianStyleStackDisplays} 已用 1~22，{@code CarianStyleTimeReversalDisplay} 用 23，
 * {@link CarianStyleFireStyleDisplay} 用 24。本类占 25~31，新增其它显示项请从 32 起。
 * </p>
 *
 * <h3>为什么单开一个类</h3>
 * <p>
 * 与 {@link CarianStyleFireStyleDisplay} 同理：{@code CarianStyleStackDisplays} 七百余行、
 * 集中登记 22 项，为加七项而整体重写的风险大于收益。本类用 {@link FMLCommonSetupEvent}
 * 自行注册，{@code StackDisplayRegistry.register} 只是往列表里追加，注册先后不影响任何行为。
 * </p>
 *
 * <h3>为什么这七项大多不画进度条（max = 0）</h3>
 * <p>
 * <b>只有誓复仇一项带进度条。</b>其余六项都传 {@code max = 0}，
 * 即「只显示数字、不画进度条」（与 {@code CarianStyleStackDisplays} 里
 * 米凯拉之刃、连击两项同款）。理由是这六项的条件<b>在很长时间里恒为真</b>：
 * </p>
 * <ul>
 *     <li>奉剑要满血——玩家出门前、探索时、战斗间隙<b>大部分时间都是满血</b>；</li>
 *     <li>碎星的两档必有一档成立——只要拿着碎星，这一行就永远在；</li>
 *     <li>红羽 / 蓝羽在残血时成立——残血战斗可能持续很久。</li>
 * </ul>
 * <p>
 * 如果给它们配进度条并让上限等于当前值，进度条会<b>常年处于满格</b>并持续触发满层燃烧，
 * 变成一个一直在闪的视野污染源。而「这一行出现了」本身就是最直接的激活信号，
 * 数字则顺带告诉你加成有多少——这已经把「激活中」表达完整了，不需要再加一条烧着的进度条。
 * </p>
 * <p>
 * 誓复仇不同：它的数值有一个<b>真实的封顶</b>（{@value #VOWED_REVENGE_MAX} 个目标，
 * 见 {@code EnchantmentVowedRevenge.MAX_COUNTED_TARGETS}），
 * 进度条填满代表「再多敌人也不会更疼了」，这个信息值得用满层燃烧强调，而且极少出现。
 * </p>
 *
 * <h3>誓复仇的范围查询做了缓存</h3>
 * <p>
 * 它是这七项里唯一需要<b>做实体查询</b>的（其余六项都只读玩家自身的生命值与装备快照）。
 * {@code StackDisplayManager} 每 3 tick 轮询一次，若每次都查一遍，
 * 一个满编服务器就会凭空多出每秒数百次 AABB 查询——而这些查询在玩家<b>站着不动</b>时
 * 也照跑不误，纯属浪费。
 * </p>
 * <p>
 * 因此本类按 {@value #VOWED_REVENGE_CACHE_TICKS} tick 缓存一次结果（见 {@link #countNearby}）。
 * 半秒的滞后对「周围有几个敌人」这种量级的信息完全无感，而查询次数降到原来的约三分之一。
 * 缓存在玩家登出时清除（见 {@link CacheCleanup}）。
 * </p>
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
public final class CarianStyleCombatStateDisplay {
    public static final int VOWED_REVENGE = 25;
    public static final int WARRIOR_BLEED = 26;
    public static final int BROKEN_STAR_OFFENSE = 27;
    public static final int BROKEN_STAR_DEFENSE = 28;
    public static final int OFFER_SWORD = 29;
    public static final int RED_FEATHERED = 30;
    public static final int BLUE_FEATHERED = 31;
    private static final String VOWED_REVENGE_ID = "vowed_revenge";
    private static final String WARRIOR_ID = "warrior";
    private static final String BROKEN_STAR_ID = "broken_star";
    private static final String OFFER_SWORD_ID = "offer_sword";
    private static final String RED_FEATHERED_ID = "red_feathered_branchsword";
    private static final String BLUE_FEATHERED_ID = "blue_feathered_branchsword";
    private static final String VOWED_REVENGE_NAME_KEY = "enchantment.carianstyle.vowed_revenge";
    private static final String WARRIOR_NAME_KEY = "enchantment.carianstyle.warrior";
    private static final String OFFER_SWORD_NAME_KEY = "enchantment.carianstyle.offer_sword";
    private static final String RED_FEATHERED_NAME_KEY = "enchantment.carianstyle.red_feathered_branchsword";
    private static final String BLUE_FEATHERED_NAME_KEY = "enchantment.carianstyle.blue_feathered_branchsword";
    private static final String BROKEN_STAR_OFFENSE_NAME_KEY = "carianstyle.hud.broken_star_offense";
    private static final String BROKEN_STAR_DEFENSE_NAME_KEY = "carianstyle.hud.broken_star_defense";
    private static final int VOWED_REVENGE_COLOR = 0x8A1420;
    private static final int WARRIOR_COLOR = 0x8A1018;
    private static final int BROKEN_STAR_OFFENSE_COLOR = 0x6A8AE0;
    private static final int BROKEN_STAR_DEFENSE_COLOR = 0x7A4AC0;
    private static final int OFFER_SWORD_COLOR = 0xFFD87A;
    private static final int RED_FEATHERED_COLOR = 0xE04A4A;
    private static final int BLUE_FEATHERED_COLOR = 0x4A9AE0;
    private static final int VOWED_REVENGE_MAX = 20;
    private static final int VOWED_REVENGE_MAX_RADIUS = 8;
    private static final int BROKEN_STAR_OFFENSE_DAY = 50;
    private static final int BROKEN_STAR_OFFENSE_NIGHT = 100;
    private static final int BROKEN_STAR_DEFENSE_DAY = 25;
    private static final int BROKEN_STAR_DEFENSE_NIGHT = 50;
    private static final int OFFER_SWORD_PERCENT_PER_LEVEL = 10;
    private static final int RED_FEATHERED_PERCENT_PER_LEVEL = 20;
    private static final int BLUE_FEATHERED_PERCENT_PER_LEVEL = 10;
    private static final int BLUE_FEATHERED_MAX_LEVEL = 9;
    private static final float FEATHERED_HEALTH_THRESHOLD = 0.2f;
    private static final int VOWED_REVENGE_CACHE_TICKS = 10;
    private static final Map<String, Enchantment> ENCHANTMENT_ID_CACHE = new ConcurrentHashMap<>();
    private static final Map<UUID, NearbyCount> NEARBY_CACHE = new HashMap<>();
    private CarianStyleCombatStateDisplay() {
    }

    private record NearbyCount(long gameTime, int count) {
    }

    public static final class CacheCleanup {
        private CacheCleanup() {
        }
    }
}

