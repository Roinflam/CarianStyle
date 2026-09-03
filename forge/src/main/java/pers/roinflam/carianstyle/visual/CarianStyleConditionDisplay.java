package pers.roinflam.carianstyle.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameShelter;
import pers.roinflam.carianstyle.enchantment.EnchantmentConcealingVeil;
import pers.roinflam.carianstyle.enchantment.EnchantmentEatShit;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
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
 * <h3>⚠ 护甲类一律走「等级之和」，不是「最高等级」</h3>
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
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CarianStyleConditionDisplay {

    // ===== 序列号（1~22 叠层，23 回溯，24 火焰流派，25~31 战斗状态）=====

    /** 黑焰庇护：当前物理减伤百分比 */
    public static final int BLACK_FLAME_SHELTER = 32;
    /** 隐匿面纱：战斗冷却剩余（冷却项） */
    public static final int CONCEALING_VEIL_BATTLE = 33;
    /** 吃屎：治疗削减剩余（冷却项） */
    public static final int EAT_SHIT_DEBUFF = 34;
    /** 快步：当前速度效果等级 */
    public static final int QUICKSTEP = 35;
    /** 岩石剑：增益窗口激活中，值为附魔等级 */
    public static final int CRAGBLADE = 36;
    /** 诺克斯之月：夜晚生效中，值为护甲累加等级 */
    public static final int MOON_OF_NOXTURA = 37;
    /** 暗弃子：夜晚生效中，值为减伤百分比 */
    public static final int DARK_ABANDONED_CHILD = 38;
    /**
     * 隐身中（徽标行）。
     * <p><b>本行被 {@code StackHudOverlay} 当作徽标处理</b>——显示「已触发」而非「×1」。
     * 若改动本常量，那边的 {@code isBadgeRow} 也要同步。</p>
     */
    public static final int STEALTH_ACTIVE = 39;

    // ===== 附魔注册 id（与各自 @AutoRegisterEnchantment 的 id 一致）=====
    private static final String BLACK_FLAME_SHELTER_ID = "black_flame_shelter";
    private static final String CONCEALING_VEIL_ID = "concealing_veil";
    private static final String QUICKSTEP_ID = "quickstep";
    private static final String CRAGBLADE_ID = "cragblade";
    private static final String MOON_OF_NOXTURA_ID = "moon_of_noxtura";
    private static final String DARK_ABANDONED_CHILD_ID = "dark_abandoned_child";

    // ===== 名称翻译键 =====
    // 六项直接复用附魔自身的名称键；吃屎与隐身状态表达的不是「附魔」而是「我身上的状态」，
    // 用附魔名会误导（中吃屎的人未必带着吃屎附魔），故各配一个新键。
    private static final String BLACK_FLAME_SHELTER_NAME_KEY = "enchantment.carianstyle.black_flame_shelter";
    private static final String CONCEALING_VEIL_NAME_KEY = "enchantment.carianstyle.concealing_veil";
    private static final String QUICKSTEP_NAME_KEY = "enchantment.carianstyle.quickstep";
    private static final String CRAGBLADE_NAME_KEY = "enchantment.carianstyle.cragblade";
    private static final String MOON_OF_NOXTURA_NAME_KEY = "enchantment.carianstyle.moon_of_noxtura";
    private static final String DARK_ABANDONED_CHILD_NAME_KEY = "enchantment.carianstyle.dark_abandoned_child";
    /**
     * 吃屎 debuff 的名称键（<b>需在 zh_cn.json 中新增</b>）。
     * <p>刻意<b>不用</b>附魔自身的名称键：这一行出现在<b>中招者</b>身上，
     * 而中招者手里未必有这个附魔。显示「吃屎」会让人以为是自己装备的东西在起作用。</p>
     */
    private static final String EAT_SHIT_NAME_KEY = "carianstyle.hud.eat_shit_debuff";
    /**
     * 隐身状态的名称键（<b>需在 zh_cn.json 中新增</b>）。
     * <p>同样不绑定到具体附魔：{@code STEALTH} 可由暗杀办法、无形刀刃、隐匿面纱
     * 三者中的任意一个赋予，显示成其中某一个的名字都是错的。</p>
     */
    private static final String STEALTH_NAME_KEY = "carianstyle.hud.stealth_active";

    // ===== 主题色（0xRRGGBB）=====
    /** 黑焰庇护：黑焰紫黑。比其它减伤项都暗，与「黑焰」的语汇一致 */
    private static final int BLACK_FLAME_SHELTER_COLOR = 0x5A3A7A;
    /** 隐匿面纱：夜雾灰蓝。冷却中＝暂时不能隐身，用冷色调 */
    private static final int CONCEALING_VEIL_COLOR = 0x6A7A96;
    /** 吃屎：污浊黄褐。一眼能看出这是个 debuff 而不是增益 */
    private static final int EAT_SHIT_COLOR = 0x8A7A2A;
    /** 快步：疾行青。与速度相关的冷色，和「越残血越快」的紧张感相称 */
    private static final int QUICKSTEP_COLOR = 0x4AC0B0;
    /** 岩石剑：岩灰棕 */
    private static final int CRAGBLADE_COLOR = 0x9A8468;
    /** 诺克斯之月：夜空靛蓝。与暗弃子区分开，两者常常同时亮着 */
    private static final int MOON_OF_NOXTURA_COLOR = 0x4A5AA0;
    /** 暗弃子：暗影深紫 */
    private static final int DARK_ABANDONED_CHILD_COLOR = 0x6A4A8A;
    /** 隐身：幽影灰青，低饱和——它表达的是「你现在很淡」 */
    private static final int STEALTH_COLOR = 0x8AA0A8;

    // ===== 数值口径（全部与附魔源码逐条核对）=====

    /**
     * 黑焰庇护每级物理减伤百分比。
     * <p>对应 {@code evt.setAmount(amount * (1 - totalLevel * 0.125f))}，即每级 12.5%。
     * 用「千分比整数」存是为了避免浮点：{@value} ‰ = 12.5%。</p>
     */
    private static final int BLACK_FLAME_SHELTER_PERMILLE_PER_LEVEL = 125;

    /**
     * 黑焰庇护 / 诺克斯之月的等级上限（仅在 {@code ConfigLoader.levelLimit} 开启时生效）。
     * <p><b>必须与两个附魔里的 {@code Math.min(totalLevel, 10)} 一致。</b>
     * 关闭 levelLimit 时两边都不钳制，HUD 也跟着不钳制。</p>
     */
    private static final int ARMOR_TOTAL_LEVEL_CAP = 10;

    /**
     * 黑焰庇护的无条件硬上限。
     * <p>
     * <b>直接引用附魔自身的常量，不复制字面量。</b>那边的 7 级上限是为了防止
     * 减伤系数归零（8 级）乃至变负（9 级+，会让挨打变成回血）而加的正确性约束，
     * 与 {@link #ARMOR_TOTAL_LEVEL_CAP} 那个可关闭的平衡开关是两回事，故<b>无条件生效</b>。
     * </p>
     * <p>
     * HUD 必须跟着一起钳——否则一件 12 级的胸甲会显示「减伤 150%」，
     * 而实际生效的是 87.5%。<b>HUD 说的话与实际效果对不上，比不显示更有害。</b>
     * </p>
     */
    private static final int BLACK_FLAME_SHELTER_MAX_LEVEL =
            EnchantmentBlackFlameShelter.MAX_EFFECTIVE_LEVEL;

    /**
     * 隐匿面纱战斗冷却的总时长（tick），用于画充能进度条。
     * <p>直接引用附魔自身的常量，不复制字面量——那边改了这边会跟着变。</p>
     */
    private static final int CONCEALING_VEIL_TOTAL_TICKS = EnchantmentConcealingVeil.BATTLE_DURATION;

    /**
     * 快步的速度等级换算除数。
     * <p>对应 {@code (int) (missingHealthPercent * 100 / 5 * totalLevel)}，
     * 即「已损失生命的每 {@value}%」折算一级。</p>
     */
    private static final int QUICKSTEP_HEALTH_STEP_PERCENT = 5;

    /**
     * 暗弃子的夜晚减伤百分比。
     * <p>对应 {@code evt.setAmount(amount * 0.9f)}，固定 10%，<b>不随等级变化</b>。
     * 语言描述里若写成随等级递增，以本实现为准。</p>
     */
    private static final int DARK_ABANDONED_CHILD_PERCENT = 10;

    /**
     * 按注册 id 解析出的附魔对象缓存。
     * <p>与 {@code CarianStyleStackDisplays.resolveEnchantment} 同款策略：
     * <b>只缓存非 null 的结果</b>，避免在注册表尚未就绪时把 null 固化下来。</p>
     */
    private static final Map<String, Enchantment> ENCHANTMENT_ID_CACHE = new ConcurrentHashMap<>();

    /** 防止重复注册（{@code register} 抛 IllegalArgumentException 会打断整个 setup） */
    private static boolean initialized = false;

    private CarianStyleConditionDisplay() {
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
        event.enqueueWork(CarianStyleConditionDisplay::register);
    }

    /**
     * 实际注册逻辑。重复调用安全。
     */
    private static void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        // ===================== 32 黑焰庇护：物理减伤百分比 =====================
        // 口径：四件护甲等级之和（levelLimit 开启时钳到 10），每级减 12.5% 物理伤害。
        // 用 armorSumLevel 而非 armorMaxLevel —— 附魔内部是逐件累加的（见类注释）。
        //
        // ⚠ 上限口径必须与附魔完全一致：附魔 v2.2 加了无条件的 7 级硬上限
        //   （8 级系数归零、9 级起变负会让挨打变回血），HUD 跟着钳到同一级。
        //   这里【不是】在遮问题 —— 问题已经在附魔侧修掉了，HUD 只是如实反映修完后的结果。
        //   上限常量直接引用附魔的 public 字段，两边不可能漂移。
        StackDisplayRegistry.register(
                BLACK_FLAME_SHELTER,
                new StackDisplayRegistry.Info(BLACK_FLAME_SHELTER_NAME_KEY, BLACK_FLAME_SHELTER_COLOR),
                (player, ctx) -> {
                    int total = armorSumLevelById(ctx, BLACK_FLAME_SHELTER_ID);
                    if (ConfigLoader.levelLimit) {
                        total = Math.min(total, ARMOR_TOTAL_LEVEL_CAP);
                    }
                    // ⭐ 与附魔 v2.2 的无条件硬上限保持同一口径（详见 BLACK_FLAME_SHELTER_MAX_LEVEL）
                    total = Math.min(total, BLACK_FLAME_SHELTER_MAX_LEVEL);
                    if (total <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    // 千分比 → 百分比，向下取整。1 级 = 12.5% 显示为 12，7 级 = 87.5% 显示为 87。
                    int percent = total * BLACK_FLAME_SHELTER_PERMILLE_PER_LEVEL / 10;
                    return new StackDisplayRegistry.Stacks(percent, 0);
                });

        // ===================== 33 隐匿面纱：战斗冷却 =====================
        // 附魔逻辑：攻击或受击时写入一条 60 tick 的冷却；潜行时若冷却未结束就不给隐身。
        // 这一行是本批唯一真正的「冷却项」——有明确总时长，进度条走充能方向，归零即消失。
        //
        // ⚠ 门控用 armorMaxLevel（任一护甲带即可），因为附魔是 ARMOR 全槽位注册的，
        //    生效条件是「有」而非「几级」，与 armorSumLevel 无关。
        StackDisplayRegistry.register(
                CONCEALING_VEIL_BATTLE,
                new StackDisplayRegistry.Info(CONCEALING_VEIL_NAME_KEY, CONCEALING_VEIL_COLOR),
                (player, ctx) -> {
                    if (armorMaxLevelById(ctx, CONCEALING_VEIL_ID) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int remaining = EnchantmentDataManager.getRemainingCooldown(
                            EnchantmentConcealingVeil.BATTLE_COOLDOWN_KEY, player.getUUID());
                    if (remaining <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(
                            remaining, CONCEALING_VEIL_TOTAL_TICKS, true);
                });

        // ===================== 34 吃屎：治疗削减剩余 =====================
        // ⚠ 这一行的显示条件与装备无关 —— 它表达的是「我中招了」，
        //    而中招者手里未必有这个附魔（多半是被别人打的）。因此不做任何装备门控。
        //
        // max 传 0（不画进度条）：debuff 的总时长是「攻击者等级 × 80」，
        // 受击方拿不到攻击者的等级，也没有任何地方记着这次的初始总量。
        // 硬凑一个总量只会让进度条乱跳，不如只显示剩余秒数。
        StackDisplayRegistry.register(
                EAT_SHIT_DEBUFF,
                new StackDisplayRegistry.Info(EAT_SHIT_NAME_KEY, EAT_SHIT_COLOR),
                (player, ctx) -> {
                    int remaining = EnchantmentDataManager.getRemainingCooldown(
                            EnchantmentEatShit.DEBUFF_KEY, player.getUUID());
                    if (remaining <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(remaining, 0, true);
                });

        // ===================== 35 快步：当前速度等级 =====================
        // 口径与附魔完全一致：
        //   totalLevel      = 四件护甲等级之和（⚠ 附魔【没有】应用 levelLimit，故这里也不钳）
        //   missingPercent  = 1 - 当前生命 / 最大生命
        //   speedLevel      = (int)(missingPercent * 100 / 5 * totalLevel)
        // 显示的是速度效果的【等级】（1 级 = 原版「迅捷 I」），不是百分比。
        //
        // 满血时 speedLevel 为 0，附魔那边直接跳过 apply，这里也返回 NONE 不占行 ——
        // 「满血时这一行消失」本身就是最直接的「现在没有加速」信号。
        StackDisplayRegistry.register(
                QUICKSTEP,
                new StackDisplayRegistry.Info(QUICKSTEP_NAME_KEY, QUICKSTEP_COLOR),
                (player, ctx) -> {
                    int total = armorSumLevelById(ctx, QUICKSTEP_ID);
                    if (total <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    float maxHealth = player.getMaxHealth();
                    if (maxHealth <= 0f) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    float missingPercent = 1f - player.getHealth() / maxHealth;
                    int speedLevel = (int) (missingPercent * 100 / QUICKSTEP_HEALTH_STEP_PERCENT * total);
                    if (speedLevel <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(speedLevel, 0);
                });

        // ===================== 36 岩石剑：增益窗口激活中 =====================
        // 附魔在攻击命中后给自己挂 200 tick 的 CRAGBLADE 动态属性，等级 = 附魔等级 - 1。
        // 这里直接查动态属性是否存在，而不是查主手装备 —— 换武器之后窗口仍然有效，
        // 按装备判断会让它提前消失，那就是在骗人。
        //
        // ⚠ 显示的是【附魔等级】（amplifier + 1），不是剩余时间：
        //   DynamicAttributeInstance 的 getDuration 在本包外不保证可见，
        //   为一个次要信息去扩大 API 暴露面不值得。「这一行在＝窗口开着」已经够用了。
        StackDisplayRegistry.register(
                CRAGBLADE,
                new StackDisplayRegistry.Info(CRAGBLADE_NAME_KEY, CRAGBLADE_COLOR),
                (player, ctx) -> {
                    int amplifier = DynamicAttributeManager.getAmplifier(player, DynamicAttributes.CRAGBLADE);
                    if (amplifier < 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(amplifier + 1, 0);
                });

        // ===================== 37 诺克斯之月：夜晚生效中 =====================
        // 附魔只在 !level().isDay() 时跑，且每秒有 2.5% 概率转移一次仇恨。
        // 白天这个附魔是完全的废件，而玩家在洞里 / 下界根本判断不了地表昼夜 ——
        // 这一行的全部价值就是回答「它现在到底在不在工作」。
        //
        // 显示护甲累加等级（levelLimit 开启时钳到 10），与附魔的 totalLevel 口径一致。
        StackDisplayRegistry.register(
                MOON_OF_NOXTURA,
                new StackDisplayRegistry.Info(MOON_OF_NOXTURA_NAME_KEY, MOON_OF_NOXTURA_COLOR),
                (player, ctx) -> {
                    if (isDay(player)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int total = armorSumLevelById(ctx, MOON_OF_NOXTURA_ID);
                    if (ConfigLoader.levelLimit) {
                        total = Math.min(total, ARMOR_TOTAL_LEVEL_CAP);
                    }
                    if (total <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(total, 0);
                });

        // ===================== 38 暗弃子：夜晚生效中 =====================
        // 夜晚：受击减伤 10%（固定值，【不随等级变化】）+ 每 tick 回复 0.075% 最大生命。
        // 白天两项都不生效，只剩「攻击时偷一个正面效果」那一段。
        //
        // 门控是主手 —— 附魔注册在 MAINHAND，减伤与回血两处也都读 getMainHandItem。
        StackDisplayRegistry.register(
                DARK_ABANDONED_CHILD,
                new StackDisplayRegistry.Info(DARK_ABANDONED_CHILD_NAME_KEY, DARK_ABANDONED_CHILD_COLOR),
                (player, ctx) -> {
                    if (isDay(player)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    if (mainHandLevelById(ctx, DARK_ABANDONED_CHILD_ID) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(DARK_ABANDONED_CHILD_PERCENT, 0);
                });

        // ===================== 39 隐身中（徽标）=====================
        // STEALTH 可由暗杀办法、无形刀刃、隐匿面纱三者中任意一个赋予，
        // 因此这一行【不做装备门控】，只看状态本身 —— 它表达的是「我现在是隐身的」。
        //
        // 这可能是本批里最有实际价值的一行：第一人称下【自己看不见自己隐身了】，
        // 而刺客赌局的 +25%/级 增伤与暴击 ×2 完全依赖这个状态，
        // 且【一次攻击就会消耗掉】。不知道自己隐没隐着，那套连招就只能靠猜。
        //
        // 以 Stacks(1, 0) 注册并被 StackHudOverlay 当作徽标行处理 ——
        // 显示「已触发」而不是毫无意义的「×1」。
        StackDisplayRegistry.register(
                STEALTH_ACTIVE,
                new StackDisplayRegistry.Info(STEALTH_NAME_KEY, STEALTH_COLOR),
                (player, ctx) -> {
                    if (!DynamicAttributeManager.has(player, DynamicAttributes.STEALTH)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(1, 0);
                });
    }

    // ==================== 判定辅助 ====================

    /**
     * 是否处于白天。
     * <p>与诺克斯之月、暗弃子两个附魔的 {@code level().isDay()} 口径完全一致。</p>
     *
     * @param player 目标玩家
     * @return 白天返回 true
     */
    private static boolean isDay(@Nonnull Player player) {
        return player.level().isDay();
    }

    /**
     * 取玩家主手上某 id 附魔的等级。
     * <p>走本轮装备附魔快照，O(1) 查表，不解析 NBT。</p>
     *
     * @param ctx    本轮装备附魔快照
     * @param enchId 附魔注册 id
     * @return 等级；附魔未注册或主手未带时为 0
     */
    private static int mainHandLevelById(@Nonnull EquipmentEnchantContext ctx, @Nonnull String enchId) {
        return ctx.mainHandLevel(resolveEnchantment(enchId));
    }

    /**
     * 取玩家四件护甲上某 id 附魔的<b>最高</b>等级。
     * <p>用于「有没有」这类门控判定。</p>
     *
     * @param ctx    本轮装备附魔快照
     * @param enchId 附魔注册 id
     * @return 最高等级；附魔未注册或全部护甲未带时为 0
     */
    private static int armorMaxLevelById(@Nonnull EquipmentEnchantContext ctx, @Nonnull String enchId) {
        return ctx.armorMaxLevel(resolveEnchantment(enchId));
    }

    /**
     * 取玩家四件护甲上某 id 附魔的<b>等级之和</b>。
     * <p>
     * <b>效果强度按四件累加计算的护甲附魔必须用这个</b>，用最高等级会显示出一个
     * 远小于实际的数字（详见类注释）。返回值<b>未做等级上限钳制</b>，
     * 由调用方按对应附魔的 {@code levelLimit} 口径自行处理。
     * </p>
     *
     * @param ctx    本轮装备附魔快照
     * @param enchId 附魔注册 id
     * @return 等级之和；附魔未注册或全部护甲未带时为 0
     */
    private static int armorSumLevelById(@Nonnull EquipmentEnchantContext ctx, @Nonnull String enchId) {
        return ctx.armorSumLevel(resolveEnchantment(enchId));
    }

    /**
     * 按注册 id 解析本模组的附魔对象（带缓存）。
     * <p>解析失败（附魔被配置禁用、或注册表尚未就绪）时不写入缓存，下次调用会重试。</p>
     *
     * @param enchId 附魔注册 id（命名空间固定为 {@link Reference#MOD_ID}）
     * @return 附魔对象；未注册时返回 null
     */
    @Nullable
    private static Enchantment resolveEnchantment(@Nonnull String enchId) {
        Enchantment cached = ENCHANTMENT_ID_CACHE.get(enchId);
        if (cached != null) {
            return cached;
        }
        Enchantment resolved = ForgeRegistries.ENCHANTMENTS.getValue(
                new ResourceLocation(Reference.MOD_ID, enchId));
        if (resolved != null) {
            ENCHANTMENT_ID_CACHE.put(enchId, resolved);
        }
        return resolved;
    }
}
