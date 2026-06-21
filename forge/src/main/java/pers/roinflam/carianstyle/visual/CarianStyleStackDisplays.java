package pers.roinflam.carianstyle.visual;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentBlood;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentMikaelaBlade;

import java.util.UUID;

/**
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
 * <b>门控（防止"切到没带该附魔的装备仍显示"）：</b>武器类（尸山血海/腐败翼剑/米凯拉之刃/居合/米莉森义肢/
 * 忍耐/祈祷一击/神皮襁褓/血/连击）要求<b>主手当前确实带有对应附魔</b>才显示；护甲类（神圣秩序/因果律）要求
 * <b>任一护甲带有该附魔</b>才显示；切走装备即隐藏（服务端数据仍按各自规则存续）。
 * 龙徽大盾的层数由动态属性自带时效，沿用原逻辑不额外门控。
 * <p>
 * 读取器仅在服务端轮询时调用，均为廉价查询。
 *
 * @author FlameForge
 */
public final class CarianStyleStackDisplays {

    // ===== 序列号（与客户端反查元数据共用，双端一致；新增附魔在此追加）=====
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

    // ===== 各附魔上限（有固定上限的在此集中，便于调整）=====
    /** 龙徽大盾物理护盾层数上限 */
    private static final int DRAGONCREST_MAX = 20;
    /** 尸横遍野击杀层数上限 */
    private static final int CORPSE_PILER_MAX = 50;
    /**
     * 腐败翼剑「连击计数」上限（与附魔内部 MAX_COMBO 保持一致）。
     * <p>注意：这是连击计数上限而非「层数」上限。附魔内部计数器最高累计到 20
     * （每 4 次攻击约 1 层增幅、至多 5 层），而 HUD 直接显示该计数器的原始值，
     * 故进度条上限必须用 20；若沿用旧值 5，连击数超过 5 时进度条会提前撑满，
     * 这正是「进度条满了其实还没满」的成因之一。</p>
     */
    private static final int CORRUPTED_WING_SWORD_MAX = 20;
    /**
     * 米莉森义肢「每等级对应的叠层数」。
     * <p>实际叠层上限随附魔等级变化：上限 = 等级 × {@value}，
     * 与附魔逻辑里 {@code level*7-1} 的 amplifier 封顶对应（HUD 显示值 count = amplifier + 1，
     * 故 count 封顶 = 等级 × 7）。原先把上限写死为固定 8 是错误的——
     * 等级 ≥ 2 时真实上限远大于 8，导致进度条在远未到顶时就撑满。</p>
     */
    private static final int MILLICENT_STACK_PER_LEVEL = 7;
    /**
     * 居合「攻击蓄力计数」上限（触发概率达 100% 的临界值）。
     * <p>居合触发概率 = 1% + 计数 × 0.5%（见 {@link EnchantmentUnsheathe}）。
     * 计数达 {@value} 时概率 = 1 + 198×0.5 = 100%，此后下次攻击必定触发居合斩并把计数重置为 0，
     * 故计数实际最高只能到 {@value}。进度条上限取此值：填充满即代表「下次攻击必出居合斩」。
     * 由于概率公式线性，进度条填充比例 ≈ 当前触发概率（count/198 ≈ probability/100）。</p>
     */
    private static final int UNSHEATHE_MAX = 198;
    /**
     * 忍耐「储存伤害」上限系数（× 最大生命 × 等级）。
     * <p>忍耐受击时累积伤害（见 {@link EnchantmentPatience}），上限 = 最大生命 × 等级 × {@value}，
     * 下次攻击时把储存值一次性追加到伤害上并清空。HUD 上限随玩家最大生命与主手附魔等级动态计算，
     * 显示的数字是「当前储存的伤害值」（非层数），进度条填充比例 = 储存值 / 上限。</p>
     */
    private static final float PATIENCE_MAX_RATIO = 0.4f;
    /**
     * 神圣秩序（圣律）吸收盾上限系数（× 最大生命）。
     * <p>神圣秩序击杀叠加吸收盾（见 {@link EnchantmentSacredOrder}），上限 = 最大生命 × {@value}。
     * 注意：吸收盾是原版共享机制，其它来源（如圣地）也会增加 absorption，
     * 因此本进度条反映的是「当前总吸收盾」（神圣秩序为主要来源但非唯一），这是机制所限的近似。</p>
     */
    private static final int SACRED_ORDER_MAX_MULTIPLIER = 3;
    /**
     * 神皮襁褓「攻击计数」上限。
     * <p>神皮襁褓每 4 次攻击触发一次治疗（见 {@link EnchantmentGodskinSwaddling}），
     * 内部计数器在 0→1→2→3 之间循环，计数到 3 时下一次攻击回血并清零。
     * 进度条上限取 {@value}：填充满（3/3）即代表「下次攻击回血」，回血后计数归零、进度条隐藏。</p>
     */
    private static final int GODSKIN_SWADDLING_MAX = 3;
    /**
     * 血「攻击计数」上限。
     * <p>血每 3 次攻击触发一次吸血（吸目标 12% 当前生命 + 自愈 + 流血，见 {@link EnchantmentBlood}），
     * 内部计数器在 0→1→2 之间循环，计数到 2 时下一次攻击触发并清零。
     * 进度条上限取 {@value}：填充满（2/2）即代表「下次攻击触发吸血」，触发后归零隐藏。</p>
     */
    private static final int BLOOD_MAX = 2;
    /**
     * 因果律「受击累积」上限（触发 AOE 反击所需的受击次数）。
     * <p>因果律每受击累积 1 层（见 {@link EnchantmentCausalityPrinciple}），满 {@value} 层对周围敌人
     * AOE 反击并清零。进度条上限取此值：填充满即代表「反击就绪」。
     * 注意：附魔有 1 秒触发冷却，冷却中计数可短暂超过 {@value}，读取器已用 Math.min 钳制显示值，
     * 故 HUD 数字不会超过 {@value}（满即代表反击就绪、正等待冷却结束）。</p>
     */
    private static final int CAUSALITY_PRINCIPLE_MAX = 5;

    // ===== 计数器/数据 key（与各附魔实现里的常量保持一致，改任一处都要同步）=====
    /** 尸山血海击杀计数 key（见 EnchantmentCorpsePiler.KILL_COUNT_KEY） */
    private static final String CORPSE_PILER_KEY = "corpse_piler_kills";
    /** 米凯拉之刃连击计数 key（见 EnchantmentMikaelaBlade.COMBO_COUNT_KEY） */
    private static final String MIKAELA_BLADE_KEY = "mikaela_blade_combo";
    /** 腐败翼剑连击计数 key（见 EnchantmentCorruptedWingSword.COMBO_COUNTER_KEY） */
    private static final String CORRUPTED_WING_SWORD_KEY = "corrupted_wing_sword_combo";
    /** 居合攻击蓄力计数 key（见 EnchantmentUnsheathe.ATTACK_COUNT_KEY） */
    private static final String UNSHEATHE_KEY = "unsheathe_attack_count";
    /** 忍耐储存伤害 key（见 EnchantmentPatience.PATIENCE_DATA_KEY，存的是 Float） */
    private static final String PATIENCE_KEY = "patience_accumulated";
    /** 神皮襁褓攻击计数 key（见 EnchantmentGodskinSwaddling.ATTACK_COUNTER） */
    private static final String GODSKIN_SWADDLING_KEY = "godskin_swaddling_attack";
    /** 血攻击计数 key（见 EnchantmentBlood.ATTACK_COUNT_KEY） */
    private static final String BLOOD_KEY = "blood_attack_count";
    /** 连击连击数 key（见 EnchantmentRepeatingThrust.STACK_COUNT_KEY） */
    private static final String REPEATING_THRUST_KEY = "repeating_thrust_stacks";
    /** 因果律受击累积 key（见 EnchantmentCausalityPrinciple.COUNTER_KEY） */
    private static final String CAUSALITY_PRINCIPLE_KEY = "causality_principle";

    /**
     * 祈祷一击永久生命上限修正器 UUID（必须与 EnchantmentPrayerfulStrike.MAX_HEALTH_MODIFIER_ID 一致）。
     * <p>祈祷一击触发时把奖励的一半累加为永久最大生命修正，上限为 {@code ConfigLoader.prayerfulStrikeMaxHealth}。
     * 这里通过该 UUID 反查玩家身上的修正器数值，作为「已累积永久生命上限」进度条。
     * 改附魔里那个 UUID 必须同步这里，否则读不到数值。</p>
     */
    private static final UUID PRAYERFUL_STRIKE_MAX_HEALTH_MODIFIER_ID =
            UUID.fromString("b55a7c8a-df03-bca7-b5ea-ec703b261525");

    private static boolean initialized = false;

    private CarianStyleStackDisplays() {
    }

    /**
     * 注册全部叠层显示项。重复调用安全。
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 龙徽大盾：受击叠物理护盾，层数 = DRAGONCREST_GREATSHIELD 的 amplifier+1，上限 20
        StackDisplayRegistry.register(
                DRAGONCREST_GREATSHIELD,
                new StackDisplayRegistry.Info("enchantment.carianstyle.dragoncrest_greatshield", 0x5AA0FF),
                player -> {
                    int amp = DynamicAttributeManager.getAmplifier(player, DynamicAttributes.DRAGONCREST_GREATSHIELD);
                    if (amp < 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(amp + 1, DRAGONCREST_MAX);
                });

        // 尸山血海：击杀计数，上限 50；主手没带该附魔则不显示
        StackDisplayRegistry.register(
                CORPSE_PILER,
                new StackDisplayRegistry.Info("enchantment.carianstyle.corpse_piler", 0xD64550),
                player -> {
                    if (!mainHandHas(player, EnchantmentCorpsePiler.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(CORPSE_PILER_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, CORPSE_PILER_MAX);
                });

        // 腐败翼剑：连击计数，上限 20（连击计数封顶值，非层数）；主手没带该附魔则不显示
        StackDisplayRegistry.register(
                CORRUPTED_WING_SWORD,
                new StackDisplayRegistry.Info("enchantment.carianstyle.corrupted_wing_sword", 0xB05CE0),
                player -> {
                    if (!mainHandHas(player, EnchantmentCorruptedWingSword.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(CORRUPTED_WING_SWORD_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, CORRUPTED_WING_SWORD_MAX);
                });

        // 米凯拉之刃：连击计数，无硬上限（上限填 0 → 只显示数字不画进度条）；主手没带该附魔则不显示
        StackDisplayRegistry.register(
                MIKAELA_BLADE,
                new StackDisplayRegistry.Info("enchantment.carianstyle.mikaela_blade", 0x5FE0C8),
                player -> {
                    if (!mainHandHas(player, EnchantmentMikaelaBlade.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(MIKAELA_BLADE_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, 0);
                });

        // 米莉森义肢：层数存于【共享的】 ATTACK_BOOST amplifier，并非专属，
        // 故先确认主手装了米莉森才显示，避免把其它来源的攻击增益误标成米莉森。
        // 上限随附魔等级变化：真实叠层上限 = 等级 × MILLICENT_STACK_PER_LEVEL，
        // 与附魔逻辑里 level*7-1 的 amplifier 封顶对应（HUD 显示值 count = amplifier + 1）；
        // 满层后附魔会把 amplifier 刷新到更高的「翻倍」档，此时 count 超过上限、进度条自然撑满。
        StackDisplayRegistry.register(
                MILLICENT_PROSTHESIS,
                new StackDisplayRegistry.Info("enchantment.carianstyle.millicent_prosthesis", 0xF2B135),
                player -> {
                    int level = mainHandLevel(player, EnchantmentMillicentProsthesis.class);
                    if (level <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int amp = DynamicAttributeManager.getAmplifier(player, DynamicAttributes.ATTACK_BOOST);
                    if (amp < 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    // 进度条上限按当前主手附魔等级动态计算，避免低估真实叠层导致进度条提前撑满
                    int max = level * MILLICENT_STACK_PER_LEVEL;
                    return new StackDisplayRegistry.Stacks(amp + 1, max);
                });

        // 居合：攻击蓄力计数，上限 198（触发概率达 100% 的临界值，满条 = 下次攻击必出居合斩）。
        // 计数仅在玩家攻击且未触发居合斩时累积（见 EnchantmentUnsheathe），触发居合斩后重置为 0；
        // 计数为 0 时（含触发后归零、首次未攻击）不显示。主手没带该附魔则不显示。
        // 因概率 = 1% + 计数×0.5% 为线性，进度条填充比例 ≈ 当前触发概率，满条即「下次必斩」。
        StackDisplayRegistry.register(
                UNSHEATHE,
                new StackDisplayRegistry.Info("enchantment.carianstyle.unsheathe", 0x88B5CC),
                player -> {
                    if (!mainHandHas(player, EnchantmentUnsheathe.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(UNSHEATHE_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, UNSHEATHE_MAX);
                });

        // 忍耐：受击储存伤害（上限 = 最大生命×等级×0.4，随等级/最大生命动态），下次攻击释放并清空。
        // 注意：getData 取出的是 Float（储存的伤害值），显示的数字是「当前储存伤害」而非层数；
        // 储存为 0 或无数据时不显示。主手没带该附魔则不显示。
        StackDisplayRegistry.register(
                PATIENCE,
                new StackDisplayRegistry.Info("enchantment.carianstyle.patience", 0xC07BB0),
                player -> {
                    int level = mainHandLevel(player, EnchantmentPatience.class);
                    if (level <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    Float accumulated = EnchantmentDataManager.getData(PATIENCE_KEY, player.getUUID());
                    if (accumulated == null || accumulated <= 0f) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int max = Math.max(1, (int) (player.getMaxHealth() * level * PATIENCE_MAX_RATIO));
                    return new StackDisplayRegistry.Stacks(Math.round(accumulated), max);
                });

        // 神圣秩序（圣律）：击杀叠加吸收盾，上限 = 最大生命×3。读取的是原版 absorption（共享机制），
        // 神圣秩序为主要来源但非唯一（圣地等也会加吸收盾），故为近似。
        // 护甲没带该附魔则不显示；吸收盾为 0 时不显示。
        StackDisplayRegistry.register(
                SACRED_ORDER,
                new StackDisplayRegistry.Info("enchantment.carianstyle.sacred_order", 0xE0C060),
                player -> {
                    if (!armorHas(player, EnchantmentSacredOrder.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    float absorption = player.getAbsorptionAmount();
                    if (absorption <= 0f) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int max = Math.max(1, (int) (player.getMaxHealth() * SACRED_ORDER_MAX_MULTIPLIER));
                    return new StackDisplayRegistry.Stacks(Math.round(absorption), max);
                });

        // 祈祷一击：累积的永久最大生命加成（趋向配置上限 prayerfulStrikeMaxHealth，默认 1000）。
        // 属长期成长指示，战斗中变化缓慢。通过 MAX_HEALTH 修正器 UUID 反查当前累积值。
        // 主手没带该附魔则不显示；尚无累积（修正器不存在或 ≤0）时不显示。
        StackDisplayRegistry.register(
                PRAYERFUL_STRIKE,
                new StackDisplayRegistry.Info("enchantment.carianstyle.prayerful_strike", 0x86C06E),
                player -> {
                    if (!mainHandHas(player, EnchantmentPrayerfulStrike.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    AttributeInstance attribute = player.getAttribute(Attributes.MAX_HEALTH);
                    if (attribute == null) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    AttributeModifier modifier = attribute.getModifier(PRAYERFUL_STRIKE_MAX_HEALTH_MODIFIER_ID);
                    if (modifier == null || modifier.getAmount() <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int max = Math.max(1, (int) ConfigLoader.prayerfulStrikeMaxHealth);
                    return new StackDisplayRegistry.Stacks((int) Math.round(modifier.getAmount()), max);
                });

        // 神皮襁褓：每 4 次攻击回血，内部计数 0→1→2→3 循环，上限 3（满条即下次攻击回血）。
        // 计数为 0（刚回血/未攻击）时不显示。主手没带该附魔则不显示。
        StackDisplayRegistry.register(
                GODSKIN_SWADDLING,
                new StackDisplayRegistry.Info("enchantment.carianstyle.godskin_swaddling", 0xCBB892),
                player -> {
                    if (!mainHandHas(player, EnchantmentGodskinSwaddling.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(GODSKIN_SWADDLING_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, GODSKIN_SWADDLING_MAX);
                });

        // 血：每 3 次攻击触发一次吸血（吸目标 12% 当前生命 + 自愈 + 流血），计数 0→1→2 循环，
        // 上限 2（满条 2/2 即下次攻击触发吸血），触发后归零隐藏。主手没带该附魔则不显示。
        StackDisplayRegistry.register(
                BLOOD,
                new StackDisplayRegistry.Info("enchantment.carianstyle.blood", 0x8E2F4A),
                player -> {
                    if (!mainHandHas(player, EnchantmentBlood.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(BLOOD_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, BLOOD_MAX);
                });

        // 连击：对同一目标连续命中累积连击数（切换目标/目标死亡即清零），伤害 = 1 + 连击×等级×5%。
        // 连击数无硬上限（200tick 内持续叠加），故上限填 0 → 只显示数字不画进度条。主手没带该附魔则不显示。
        StackDisplayRegistry.register(
                REPEATING_THRUST,
                new StackDisplayRegistry.Info("enchantment.carianstyle.repeating_thrust", 0xE07A50),
                player -> {
                    if (!mainHandHas(player, EnchantmentRepeatingThrust.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(REPEATING_THRUST_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(count, 0);
                });

        // 因果律：每受击累积 1 层，满 5 层对周围敌人 AOE 反击并清零（受 1 秒触发冷却限制）。
        // 上限 5（满条即反击就绪）。冷却中计数可能短暂超过 5，故用 Math.min 钳制显示值，避免 HUD 出现 >5 的数字。
        // 护甲没带该附魔则不显示；计数为 0 时不显示。
        StackDisplayRegistry.register(
                CAUSALITY_PRINCIPLE,
                new StackDisplayRegistry.Info("enchantment.carianstyle.causality_principle", 0x7268C0),
                player -> {
                    if (!armorHas(player, EnchantmentCausalityPrinciple.class)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int count = EnchantmentDataManager.getCounter(CAUSALITY_PRINCIPLE_KEY, player.getUUID());
                    return new StackDisplayRegistry.Stacks(Math.min(count, CAUSALITY_PRINCIPLE_MAX), CAUSALITY_PRINCIPLE_MAX);
                });
    }

    /**
     * 判断玩家主手物品是否带有指定附魔。
     *
     * @param player 玩家
     * @param clazz  附魔类
     * @return 主手带有该附魔（等级>0）返回 true
     */
    private static boolean mainHandHas(Player player, Class<? extends EnchantmentBase> clazz) {
        return mainHandLevel(player, clazz) > 0;
    }

    /**
     * 取玩家主手物品上指定附魔的等级。
     *
     * @param player 玩家
     * @param clazz  附魔类
     * @return 等级；附魔未注册或主手未带时为 0
     */
    private static int mainHandLevel(Player player, Class<? extends EnchantmentBase> clazz) {
        Enchantment ench = EnchantmentRegistry.getEnchantmentByClass(clazz);
        if (ench == null) {
            return 0;
        }
        return EnchantmentHelper.getItemEnchantmentLevel(ench, player.getMainHandItem());
    }

    /**
     * 判断玩家护甲（4 件）上是否带有指定附魔。
     * <p>用于护甲类附魔的显示门控：任一护甲槽带有该附魔即返回 true。</p>
     *
     * @param player 玩家
     * @param clazz  附魔类
     * @return 任一护甲带有该附魔（等级>0）返回 true
     */
    private static boolean armorHas(Player player, Class<? extends EnchantmentBase> clazz) {
        Enchantment ench = EnchantmentRegistry.getEnchantmentByClass(clazz);
        if (ench == null) {
            return false;
        }
        for (ItemStack armor : player.getArmorSlots()) {
            if (!armor.isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(ench, armor) > 0) {
                return true;
            }
        }
        return false;
    }
}
