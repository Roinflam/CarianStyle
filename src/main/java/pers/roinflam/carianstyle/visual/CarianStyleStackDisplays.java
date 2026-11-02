package pers.roinflam.carianstyle.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.enchantment.EnchantmentCausalityPrinciple;
import pers.roinflam.carianstyle.enchantment.EnchantmentCorruptedWingSword;
import pers.roinflam.carianstyle.enchantment.EnchantmentGodskinSwaddling;
import pers.roinflam.carianstyle.enchantment.EnchantmentMillicentProsthesis;
import pers.roinflam.carianstyle.enchantment.EnchantmentSacredOrder;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentCorpsePiler;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentPatience;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentPrayerfulStrike;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentRepeatingThrust;
import pers.roinflam.carianstyle.enchantment.combatskill.EnchantmentUnsheathe;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentEpilepsySpread;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentScarletLonia;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentBlood;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentMikaelaBlade;
import pers.roinflam.carianstyle.utils.Reference;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PORT-STUB(V2)
 *
 * 叠层显示注册入口（双端通用，需在公共初始化阶段调用一次 {@link #init()}）。
 * <p>
 * 序列号集中在此管理。已按各附魔的真实状态存储接好读取器，每个读取器返回
 * {@link StackDisplayRegistry.Stacks}（当前层数/数值 + 当前上限）：
 * <ul>
 *     <li>龙徽大盾、米莉森义肢 —— 层数存于 {@link DynamicAttributeManager} 的 amplifier；</li>
 *     <li>尸山血海、米凯拉之刃、腐败翼剑、居合、神皮襁褓、血、连击、因果律 —— 计数存于 {@link EnchantmentDataManager} 计数器；</li>
 *     <li>忍耐 —— 储存的伤害值存于 {@link EnchantmentDataManager} 的通用数据（Float）；</li>
 *     <li>神圣秩序（圣律）—— 读取原版吸收盾 {@code getAbsorptionAmount()}（共享机制，为近似）；</li>
 *     <li>祈祷一击 —— 读取 MAX_HEALTH 属性修正器数值（累积的永久生命上限）。</li>
 * </ul>
 * <b>上限（关键）：</b>HUD 进度条填充比例 = count / max，因此 max 必须等于该项的<b>真实封顶值</b>，
 * 否则会出现「进度条满了其实还没满」或「永远填不满」。
 * <ul>
 *     <li>龙徽大盾 20（amplifier 封顶 19，count = amp+1 = 20）；</li>
 *     <li>尸山血海 50（计数封顶 50）；</li>
 *     <li>腐败翼剑 20（连击计数封顶 MAX_COMBO=20，HUD 显示原始计数，故上限是 20 而非「层数 5」）；</li>
 *     <li>米莉森义肢上限<b>随附魔等级变化</b>（= 等级 × 7，对应附魔逻辑里 amplifier 的 {@code level*7-1} 封顶）；</li>
 *     <li>居合 198（触发概率 = 1% + 计数×0.5%，计数达 198 时概率 = 100%、下次攻击必触发并把计数重置；
 *         进度条填充满即代表「下次攻击必出居合斩」，因公式线性，填充比例≈当前触发概率）；</li>
 *     <li>忍耐 上限 = 最大生命 × 等级 × 0.4（随最大生命与等级动态；显示值是储存伤害，非层数）；</li>
 *     <li>神圣秩序（圣律）上限 = 最大生命 × 3（吸收盾，原版共享机制为近似）；</li>
 *     <li>祈祷一击 上限 = 配置 prayerfulStrikeMaxHealth（默认 1000，长期成长、变化缓慢）；</li>
 *     <li>神皮襁褓 3（计数 0→3 循环，满条即下次攻击回血）；</li>
 *     <li>血 2（计数 0→2 循环，满条即下次攻击触发吸血）；</li>
 *     <li>因果律 5（每受击累积 1 层，满 5 层 AOE 反击；读取器已钳制显示值，数字不超过 5）；</li>
 *     <li>米凯拉之刃、连击 无硬上限返回 0（HUD 不画进度条，只显示连击数字）。</li>
 * </ul>
 * <p>
 * <b>v2 新增：死亡/濒死触发类附魔的冷却倒计时（serialId 16–20，冷却模式 cooldown=true）。</b>
 * 复用现有「冷却倒计时」管线（与圣血罗妮亚 14 / 癫火蔓延 15 同款）：读取器返回
 * {@code Stacks(剩余冷却 tick, 总冷却 tick, true)}，HUD 显示「剩余秒数 + 充能进度条」，
 * 冷却结束（剩余 0）即消失、不触发满层燃烧。全部为护甲类附魔，沿用「任一护甲带该附魔才显示」的门控。
 * <ul>
 *     <li>满月 full_moon —— 键 full_moon_cooldown；冷却为变量（白天 3600 / 夜晚 1800 tick），
 *         进度条上限固定取白天值 3600（夜晚触发时充能条起点约为半满，但<b>显示的剩余秒数恒准确</b>，
 *         因为秒数取自真实剩余 tick，与上限无关）；</li>
 *     <li>死诞者 living_corpse —— 键 living_corpse_cooldown；冷却 4800 tick；</li>
 *     <li>回溯 time_reversal —— 键 time_reversal_cooldown；冷却 6000 tick；</li>
 *     <li>古龙雷电 ancient_dragon_lightning —— 键 ancient_dragon_lightning（<b>无 _cooldown 后缀</b>）；冷却 1800 tick；</li>
 *     <li>巨剑方阵 greatblade_phalanx —— 键 greatblade_phalanx（<b>无 _cooldown 后缀</b>）；冷却 6000 tick（全身四件套）。</li>
 * </ul>
 * <p>
 * <b>v2 新增：附魔联动徽标（serialId 21–22，普通模式、无进度条的「激活」徽标）。</b>
 * 当一组互相加强的附魔同时装备到位时，在 HUD 上亮一个徽标提示玩家「联动已生效」。徽标用
 * {@code Stacks(1, 0)}（上限 0 → 不画进度条，仅显示名称与「×1」）；联动条件不满足时返回
 * {@link StackDisplayRegistry.Stacks#NONE} 隐藏。附魔的解析采用与
 * {@code AuraDisplayRegistry} 一致的 {@link ResourceLocation} 方式（按注册 id 取附魔，
 * 不依赖具体附魔类），从而无需为联动伙伴附魔新增 import：
 * <ul>
 *     <li>血之共鸣 —— 主手武器同时带有 blood + blood_slash + blood_collection（触发 7 级失血、回血翻倍）；</li>
 *     <li>月之共鸣 —— 主手武器带 dark_moon 且任一护甲带 full_moon（增伤/吸血/续命全部翻倍）。</li>
 * </ul>
 * 注意：联动徽标需要两个新的语言键（见 {@link #BLOOD_RESONANCE_NAME_KEY} /
 * {@link #MOON_RESONANCE_NAME_KEY}），请在 zh_cn.json 中补上对应翻译，否则 HUD 会显示原始键名。
 * <p>
 * <b>门控（防止"切到没带该附魔的装备仍显示"）：</b>武器类（尸山血海/腐败翼剑/米凯拉之刃/居合/米莉森义肢/
 * 忍耐/祈祷一击/神皮襁褓/血/连击）要求<b>主手当前确实带有对应附魔</b>才显示；护甲类（神圣秩序/因果律/
 * 五个死亡附魔）要求<b>任一护甲带有该附魔</b>才显示；切走装备即隐藏（服务端数据仍按各自规则存续）。
 * 龙徽大盾的层数由动态属性自带时效，沿用原逻辑不额外门控。
 * <p>
 * <b>v3 性能优化（显示与行为完全不变，仅改数据来源）：</b>
 * <ul>
 *     <li><b>门控判断改查快照：</b>全部读取器改为 {@link StackDisplayRegistry.ContextualStackProvider}，
 *         接收 {@link EquipmentEnchantContext}（本玩家本轮的装备附魔快照，由
 *         {@code StackDisplayManager} 每轮统一构建一次）。原先每个读取器各自调用
 *         {@code EnchantmentHelper.getItemEnchantmentLevel} 解析装备 NBT，同一件装备一轮内被
 *         反复解析数十遍；现在全部降为 O(1) 查表，单玩家单轮的 NBT 反序列化次数由数十次降为至多 5 次。</li>
 *     <li><b>按 id 解析附魔加缓存：</b>{@link #resolveEnchantment(String)} 原先每次调用都
 *         {@code ResourceLocation.parse(...)} 并查一次注册表，而死亡类门控与两个联动徽标每轮要调用约 10 次；
 *         现改为解析成功后写入 {@link #ENCHANTMENT_ID_CACHE}，后续直接命中缓存。
 *         与 {@code AuraDisplayRegistry.EnchantAuraDetector#resolve()} 同款「仅缓存非 null」策略，
 *         避免在注册表就绪前把 null 固化下来。</li>
 *     <li>按类解析（{@link EnchantmentRegistry#getEnchantmentByClass}）本身就是 HashMap 查询，
 *         已足够廉价，未作改动。</li>
 * </ul>
 * <p>
 * 读取器仅在服务端轮询时调用，均为廉价查询。
 *
 * @author RoinFlam
 * @version 3
 */
@Deprecated
public final class CarianStyleStackDisplays {
    public static final int DRAGONCREST_GREATSHIELD = 1;
    public static final int MILLICENT_PROSTHESIS = 2;
    public static final int CORPSE_PILER = 3;
    public static final int CORRUPTED_WING_SWORD = 4;
    public static final int MIKAELA_BLADE = 5;
    public static final int UNSHEATHE = 6;
    public static final int PATIENCE = 7;
    public static final int SACRED_ORDER = 8;
    public static final int PRAYERFUL_STRIKE = 9;
    public static final int GODSKIN_SWADDLING = 10;
    public static final int BLOOD = 11;
    public static final int REPEATING_THRUST = 12;
    public static final int CAUSALITY_PRINCIPLE = 13;
    public static final int SCARLET_LONIA = 14;
    public static final int EPILEPSY_SPREAD = 15;
    public static final int FULL_MOON = 16;
    public static final int LIVING_CORPSE = 17;
    public static final int TIME_REVERSAL = 18;
    public static final int ANCIENT_DRAGON_LIGHTNING = 19;
    public static final int GREATBLADE_PHALANX = 20;
    public static final int BLOOD_RESONANCE = 21;
    public static final int MOON_RESONANCE = 22;
    private static final int DRAGONCREST_MAX = 20;
    private static final int CORPSE_PILER_MAX = 50;
    private static final int CORRUPTED_WING_SWORD_MAX = 20;
    private static final int MILLICENT_STACK_PER_LEVEL = 7;
    private static final int UNSHEATHE_MAX = 198;
    private static final float PATIENCE_MAX_RATIO = 0.4f;
    private static final int SACRED_ORDER_MAX_MULTIPLIER = 3;
    private static final int GODSKIN_SWADDLING_MAX = 3;
    private static final int BLOOD_MAX = 2;
    private static final int CAUSALITY_PRINCIPLE_MAX = 5;
    private static final int FULL_MOON_COOLDOWN_TICKS = 3600;
    private static final int LIVING_CORPSE_COOLDOWN_TICKS = 4800;
    private static final int TIME_REVERSAL_COOLDOWN_TICKS = 6000;
    private static final int ANCIENT_DRAGON_LIGHTNING_COOLDOWN_TICKS = 1800;
    private static final int GREATBLADE_PHALANX_COOLDOWN_TICKS = 6000;
    private static final String CORPSE_PILER_KEY = "corpse_piler_kills";
    private static final String MIKAELA_BLADE_KEY = "mikaela_blade_combo";
    private static final String CORRUPTED_WING_SWORD_KEY = "corrupted_wing_sword_combo";
    private static final String UNSHEATHE_KEY = "unsheathe_attack_count";
    private static final String PATIENCE_KEY = "patience_accumulated";
    private static final String GODSKIN_SWADDLING_KEY = "godskin_swaddling_attack";
    private static final String BLOOD_KEY = "blood_attack_count";
    private static final String REPEATING_THRUST_KEY = "repeating_thrust_stacks";
    private static final String CAUSALITY_PRINCIPLE_KEY = "causality_principle";
    private static final String SCARLET_LONIA_COOLDOWN_KEY = "scarlet_lonia_cooldown";
    private static final String EPILEPSY_SPREAD_COOLDOWN_KEY = "epilepsy_spread_cooldown";
    private static final int DEAD_COOLDOWN_TICKS = 1800;
    private static final String FULL_MOON_COOLDOWN_KEY = "full_moon_cooldown";
    private static final String LIVING_CORPSE_COOLDOWN_KEY = "living_corpse_cooldown";
    private static final String TIME_REVERSAL_COOLDOWN_KEY = "time_reversal_cooldown";
    private static final String ANCIENT_DRAGON_LIGHTNING_COOLDOWN_KEY = "ancient_dragon_lightning";
    private static final String GREATBLADE_PHALANX_COOLDOWN_KEY = "greatblade_phalanx";
    private static final String FULL_MOON_ID = "full_moon";
    private static final String BLOOD_ID = "blood";
    private static final String BLOOD_SLASH_ID = "blood_slash";
    private static final String BLOOD_COLLECTION_ID = "blood_collection";
    private static final String DARK_MOON_ID = "dark_moon";
    private static final String BLOOD_RESONANCE_NAME_KEY = "carianstyle.hud.blood_resonance";
    private static final String MOON_RESONANCE_NAME_KEY = "carianstyle.hud.moon_resonance";
    private static final int BLOOD_RESONANCE_COLOR = 0xB01030;
    private static final int MOON_RESONANCE_COLOR = 0x7E9CFF;
    private static final UUID PRAYERFUL_STRIKE_MAX_HEALTH_MODIFIER_ID = UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");
    private static final Map<String, Enchantment> ENCHANTMENT_ID_CACHE = new ConcurrentHashMap<>();
    private CarianStyleStackDisplays() {
    }
    public static void init() {
    }
}

