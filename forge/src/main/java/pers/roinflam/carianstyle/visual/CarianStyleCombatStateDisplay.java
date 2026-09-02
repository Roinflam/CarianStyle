package pers.roinflam.carianstyle.visual;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.ForgeRegistries;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentWarrior;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
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
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CarianStyleCombatStateDisplay {

    // ===== 序列号（1~22 见 CarianStyleStackDisplays，23 回溯，24 火焰流派）=====
    /** 誓复仇：当前计入加成的周围目标数 */
    public static final int VOWED_REVENGE = 25;
    /** 战士：尚未结算的流血伤害 */
    public static final int WARRIOR_BLEED = 26;
    /** 碎星·攻势：当前增伤百分比 */
    public static final int BROKEN_STAR_OFFENSE = 27;
    /** 碎星·守势：当前减伤百分比 */
    public static final int BROKEN_STAR_DEFENSE = 28;
    /** 奉剑：满血增伤百分比 */
    public static final int OFFER_SWORD = 29;
    /** 红羽枝剑：残血增伤百分比 */
    public static final int RED_FEATHERED = 30;
    /** 蓝羽枝剑：残血减伤百分比 */
    public static final int BLUE_FEATHERED = 31;

    // ===== 附魔注册 id（与各自 @AutoRegisterEnchantment 的 id 一致）=====
    private static final String VOWED_REVENGE_ID = "vowed_revenge";
    private static final String WARRIOR_ID = "warrior";
    private static final String BROKEN_STAR_ID = "broken_star";
    private static final String OFFER_SWORD_ID = "offer_sword";
    private static final String RED_FEATHERED_ID = "red_feathered_branchsword";
    private static final String BLUE_FEATHERED_ID = "blue_feathered_branchsword";

    // ===== 名称翻译键 =====
    // 六项直接复用附魔自身的名称键；碎星拆成攻守两行，需要两个新键。
    private static final String VOWED_REVENGE_NAME_KEY = "enchantment.carianstyle.vowed_revenge";
    private static final String WARRIOR_NAME_KEY = "enchantment.carianstyle.warrior";
    private static final String OFFER_SWORD_NAME_KEY = "enchantment.carianstyle.offer_sword";
    private static final String RED_FEATHERED_NAME_KEY = "enchantment.carianstyle.red_feathered_branchsword";
    private static final String BLUE_FEATHERED_NAME_KEY = "enchantment.carianstyle.blue_feathered_branchsword";
    /**
     * 碎星·攻势的名称键（<b>需在 zh_cn.json 中新增</b>，否则 HUD 会显示原始键名）。
     * <p>碎星必须拆成两行：增伤与减伤是互斥的两档，共用一行的话
     * 「50」既可能是「增伤 50%」也可能是「减伤 50%」，完全读不出来。</p>
     */
    private static final String BROKEN_STAR_OFFENSE_NAME_KEY = "carianstyle.hud.broken_star_offense";
    /** 碎星·守势的名称键（<b>需在 zh_cn.json 中新增</b>） */
    private static final String BROKEN_STAR_DEFENSE_NAME_KEY = "carianstyle.hud.broken_star_defense";

    // ===== 主题色（0xRRGGBB）=====
    /** 誓复仇：誓约暗红，与世界特效的倒三角誓印同色系 */
    private static final int VOWED_REVENGE_COLOR = 0x8A1420;
    /** 战士：战血暗红。刻意比誓复仇更暗——它表达的是「你正在掉血」而非增益 */
    private static final int WARRIOR_COLOR = 0x8A1018;
    /** 碎星·攻势：星蓝 */
    private static final int BROKEN_STAR_OFFENSE_COLOR = 0x6A8AE0;
    /** 碎星·守势：夜紫。与攻势区分开，扫一眼颜色就知道自己在哪一档 */
    private static final int BROKEN_STAR_DEFENSE_COLOR = 0x7A4AC0;
    /** 奉剑：金边苍白 */
    private static final int OFFER_SWORD_COLOR = 0xFFD87A;
    /** 红羽枝剑：赤羽红 */
    private static final int RED_FEATHERED_COLOR = 0xE04A4A;
    /** 蓝羽枝剑：翠羽蓝 */
    private static final int BLUE_FEATHERED_COLOR = 0x4A9AE0;

    // ===== 数值口径（全部与附魔源码逐条核对）=====

    /**
     * 誓复仇计入加成的目标数上限。
     * <p><b>必须与 {@code EnchantmentVowedRevenge.MAX_COUNTED_TARGETS} 一致</b>——
     * 那边超过这个数就不再增伤了，HUD 若还继续往上数，玩家会以为伤害还在涨。</p>
     */
    private static final int VOWED_REVENGE_MAX = 20;
    /**
     * 誓复仇的搜索半径上限。
     * <p><b>必须与 {@code EnchantmentVowedRevenge.MAX_SEARCH_RADIUS} 一致。</b></p>
     */
    private static final int VOWED_REVENGE_MAX_RADIUS = 8;

    /**
     * 碎星白天增伤百分比。
     * <p>对应 {@code evt.setAmount(amount * 1.5f)}，即 +50%。</p>
     */
    private static final int BROKEN_STAR_OFFENSE_DAY = 50;
    /** 碎星夜晚增伤百分比（对应 ×2，即 +100%），同时作为攻势进度条的满值参考 */
    private static final int BROKEN_STAR_OFFENSE_NIGHT = 100;
    /** 碎星白天减伤百分比（对应 ×0.75） */
    private static final int BROKEN_STAR_DEFENSE_DAY = 25;
    /** 碎星夜晚减伤百分比（对应 ×0.5） */
    private static final int BROKEN_STAR_DEFENSE_NIGHT = 50;

    /** 奉剑每级增伤百分比（对应 {@code ctx.getDamage() * level * 0.1f}） */
    private static final int OFFER_SWORD_PERCENT_PER_LEVEL = 10;
    /** 红羽枝剑每级增伤百分比（对应 {@code ctx.getDamage() * level * 0.2f}） */
    private static final int RED_FEATHERED_PERCENT_PER_LEVEL = 20;
    /** 蓝羽枝剑每级减伤百分比 */
    private static final int BLUE_FEATHERED_PERCENT_PER_LEVEL = 10;
    /**
     * 蓝羽枝剑参与计算的最高等级。
     * <p><b>必须与 {@code EnchantmentBlueFeatheredBranchsword.MAX_EFFECTIVE_LEVEL} 一致</b>——
     * 那边把等级压到 9 级封顶（减伤上限 90%），HUD 若按原始等级算，
     * 高等级武器会显示一个根本达不到的减伤数字。</p>
     */
    private static final int BLUE_FEATHERED_MAX_LEVEL = 9;

    /** 红羽 / 蓝羽的触发血量比例（{@code health <= maxHealth * 0.2}） */
    private static final float FEATHERED_HEALTH_THRESHOLD = 0.2f;

    /** 誓复仇范围查询的缓存有效期（tick） */
    private static final int VOWED_REVENGE_CACHE_TICKS = 10;

    /**
     * 按注册 id 解析出的附魔对象缓存。
     * <p>与 {@code CarianStyleStackDisplays.resolveEnchantment} 同款策略：
     * <b>只缓存非 null 的结果</b>，避免在注册表尚未就绪时把 null 固化下来。</p>
     */
    private static final Map<String, Enchantment> ENCHANTMENT_ID_CACHE = new ConcurrentHashMap<>();

    /**
     * 誓复仇周围目标数的缓存（玩家 UUID -> 上次查询）。
     * <p>仅服务端主线程访问（读取器只在轮询时调用），故用普通 {@link HashMap}。
     * 登出时由 {@link CacheCleanup} 清除，避免下线玩家的条目长期滞留。</p>
     */
    private static final Map<UUID, NearbyCount> NEARBY_CACHE = new HashMap<>();

    /** 防止重复注册（{@code register} 抛 IllegalArgumentException 会打断整个 setup） */
    private static boolean initialized = false;

    private CarianStyleCombatStateDisplay() {
    }

    /**
     * 誓复仇范围查询的一次缓存结果。
     *
     * @param gameTime 查询时的世界游戏时刻（tick）
     * @param count    当时数到的目标数（已按上限钳制）
     */
    private record NearbyCount(long gameTime, int count) {
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
        event.enqueueWork(CarianStyleCombatStateDisplay::register);
    }

    /**
     * 实际注册逻辑。重复调用安全。
     */
    private static void register() {
        if (initialized) {
            return;
        }
        initialized = true;

        // ===================== 25 誓复仇：周围目标数 =====================
        // 伤害加成 = 伤害 × 等级 × 目标数 × 2.5%，目标数封顶 20。
        // 这是本批唯一带进度条的一项：填满即代表「再多敌人也不会更疼了」。
        // 搜索半径 = min(等级×2, 8)，与附魔完全一致。
        StackDisplayRegistry.register(
                VOWED_REVENGE,
                new StackDisplayRegistry.Info(VOWED_REVENGE_NAME_KEY, VOWED_REVENGE_COLOR),
                (player, ctx) -> {
                    int level = mainHandLevelById(ctx, VOWED_REVENGE_ID);
                    if (level <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int radius = Math.min(level * 2, VOWED_REVENGE_MAX_RADIUS);
                    int count = countNearby(player, radius);
                    if (count <= 0) {
                        // 周围一个都没有时这个附魔不产生任何加成，不占 HUD 行
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(count, VOWED_REVENGE_MAX);
                });

        // ===================== 26 战士：流血剩余 =====================
        // 战士把受到伤害的一半转成 60 tick 内的持续流失。玩家会看到自己在没人打的时候
        // 还在掉血，这一行回答的就是「还要掉多少才停」。
        // 数据源是 DamageOverTimeManager 里打了 EnchantmentWarrior.DOT_TAG 标签的条目，
        // 直接按标签取，不会把癫火等其它附魔的持续伤害算进来。
        // max=0（只显示数字）：若给它配上限等于初始总量，进度条会在每次受击的瞬间满格，
        // 每挨一下就闪一次满层燃烧，反而成了干扰。
        StackDisplayRegistry.register(
                WARRIOR_BLEED,
                new StackDisplayRegistry.Info(WARRIOR_NAME_KEY, WARRIOR_COLOR),
                (player, ctx) -> {
                    if (mainHandLevelById(ctx, WARRIOR_ID) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    float remaining = DamageOverTimeManager.getRemainingDamage(player, EnchantmentWarrior.DOT_TAG);
                    if (remaining <= 0f) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    // 向上取整：剩 0.4 点伤害也该显示成 1 而不是 0，
                    // 否则最后半秒会出现「明明还在掉血、HUD 却显示 0」
                    int display = (int) Math.ceil(remaining);
                    return new StackDisplayRegistry.Stacks(display, 0);
                });

        // ===================== 27 碎星·攻势 =====================
        // 条件：生命值 >= 50%（注意是 >=，与附魔的 getHealth() >= getMaxHealth() / 2 一致）。
        // 白天 ×1.5（+50%）、夜晚 ×2（+100%）。
        StackDisplayRegistry.register(
                BROKEN_STAR_OFFENSE,
                new StackDisplayRegistry.Info(BROKEN_STAR_OFFENSE_NAME_KEY, BROKEN_STAR_OFFENSE_COLOR),
                (player, ctx) -> {
                    if (mainHandLevelById(ctx, BROKEN_STAR_ID) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    if (player.getHealth() < player.getMaxHealth() / 2) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int percent = isNight(player) ? BROKEN_STAR_OFFENSE_NIGHT : BROKEN_STAR_OFFENSE_DAY;
                    return new StackDisplayRegistry.Stacks(percent, 0);
                });

        // ===================== 28 碎星·守势 =====================
        // 条件：生命值 < 50%（严格小于，与攻势的 >= 互补、无重叠也无空档）。
        // 白天 ×0.75（减伤 25%）、夜晚 ×0.5（减伤 50%）。
        StackDisplayRegistry.register(
                BROKEN_STAR_DEFENSE,
                new StackDisplayRegistry.Info(BROKEN_STAR_DEFENSE_NAME_KEY, BROKEN_STAR_DEFENSE_COLOR),
                (player, ctx) -> {
                    if (mainHandLevelById(ctx, BROKEN_STAR_ID) <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    if (player.getHealth() >= player.getMaxHealth() / 2) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int percent = isNight(player) ? BROKEN_STAR_DEFENSE_NIGHT : BROKEN_STAR_DEFENSE_DAY;
                    return new StackDisplayRegistry.Stacks(percent, 0);
                });

        // ===================== 29 奉剑：满血激活 =====================
        // 附魔里是「getHealth() < getMaxHealth() 就 return」，故激活条件为满血。
        // 这一行出现即代表加成正在生效，掉一滴血立刻消失——这正是它最需要被看见的时刻。
        // 注意：附魔没有对该附魔应用 levelLimit，故这里也用原始等级，保持一致。
        StackDisplayRegistry.register(
                OFFER_SWORD,
                new StackDisplayRegistry.Info(OFFER_SWORD_NAME_KEY, OFFER_SWORD_COLOR),
                (player, ctx) -> {
                    int level = mainHandLevelById(ctx, OFFER_SWORD_ID);
                    if (level <= 0) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    if (player.getHealth() < player.getMaxHealth()) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(level * OFFER_SWORD_PERCENT_PER_LEVEL, 0);
                });

        // ===================== 30 红羽枝剑：残血激活 =====================
        // 条件：生命值 <= 20%。增伤 = 等级 × 20%。附魔未应用 levelLimit，此处同样用原始等级。
        StackDisplayRegistry.register(
                RED_FEATHERED,
                new StackDisplayRegistry.Info(RED_FEATHERED_NAME_KEY, RED_FEATHERED_COLOR),
                (player, ctx) -> {
                    int level = mainHandLevelById(ctx, RED_FEATHERED_ID);
                    if (level <= 0 || !isLowHealth(player)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    return new StackDisplayRegistry.Stacks(level * RED_FEATHERED_PERCENT_PER_LEVEL, 0);
                });

        // ===================== 31 蓝羽枝剑：残血激活 =====================
        // 条件：生命值 <= 20%。减伤 = 等级 × 10%，等级封顶 9（减伤上限 90%）。
        // 这里必须跟着附魔一起封顶，否则一把 20 级的蓝羽会显示「200%」这种不存在的数字。
        StackDisplayRegistry.register(
                BLUE_FEATHERED,
                new StackDisplayRegistry.Info(BLUE_FEATHERED_NAME_KEY, BLUE_FEATHERED_COLOR),
                (player, ctx) -> {
                    int level = mainHandLevelById(ctx, BLUE_FEATHERED_ID);
                    if (level <= 0 || !isLowHealth(player)) {
                        return StackDisplayRegistry.Stacks.NONE;
                    }
                    int effective = Math.min(level, BLUE_FEATHERED_MAX_LEVEL);
                    return new StackDisplayRegistry.Stacks(effective * BLUE_FEATHERED_PERCENT_PER_LEVEL, 0);
                });
    }

    // ==================== 判定辅助 ====================

    /**
     * 是否处于夜晚。
     * <p>与碎星附魔的 {@code !level().isDay()} 口径完全一致。</p>
     *
     * @param player 目标玩家
     * @return 夜晚返回 true
     */
    private static boolean isNight(@Nonnull Player player) {
        return !player.level().isDay();
    }

    /**
     * 是否处于红羽 / 蓝羽的触发血线以下。
     * <p>与两个附魔的 {@code getHealth() <= getMaxHealth() * 0.2} 口径一致。</p>
     *
     * @param player 目标玩家
     * @return 生命值 &le; 20% 返回 true
     */
    private static boolean isLowHealth(@Nonnull Player player) {
        return player.getHealth() <= player.getMaxHealth() * FEATHERED_HEALTH_THRESHOLD;
    }

    /**
     * 数一遍玩家周围计入誓复仇加成的目标数（带缓存）。
     * <p>
     * 查询本身与 {@code EnchantmentVowedRevenge} 用的是<b>同一个方法、同一组参数</b>
     * （{@code EntityUtil.getNearbyEntities}，排除自己），因此不存在
     * 「HUD 数出 5 个、实际按 4 个算」这类口径不一致。
     * </p>
     * <p>
     * 结果按 {@value #VOWED_REVENGE_CACHE_TICKS} tick 缓存：
     * {@code StackDisplayManager} 每 3 tick 轮询一次，不缓存的话满编服务器每秒会多出
     * 数百次 AABB 查询，而且玩家站着不动时也照跑。半秒滞后对「周围有几个」无感。
     * </p>
     * <p>
     * 返回值已按 {@value #VOWED_REVENGE_MAX} 钳制——超过这个数附魔就不再增伤了，
     * HUD 继续往上数会让玩家误以为伤害还在涨。
     * </p>
     *
     * @param player 目标玩家
     * @param radius 搜索半径（格），由调用方按 {@code min(等级×2, 8)} 算好
     * @return 计入加成的目标数（0 ~ {@value #VOWED_REVENGE_MAX}）
     */
    private static int countNearby(@Nonnull Player player, int radius) {
        long now = player.level().getGameTime();
        UUID uuid = player.getUUID();
        NearbyCount cached = NEARBY_CACHE.get(uuid);
        if (cached != null && now - cached.gameTime() < VOWED_REVENGE_CACHE_TICKS) {
            return cached.count();
        }

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                player,
                radius,
                entity -> !entity.equals(player)
        );
        int count = Math.min(entities.size(), VOWED_REVENGE_MAX);
        NEARBY_CACHE.put(uuid, new NearbyCount(now, count));
        return count;
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

    /**
     * 缓存清理监听器。
     * <p>
     * 独立成嵌套类，是因为外层类订阅的是 MOD 事件总线（注册用），
     * 而玩家登出事件在 FORGE 总线上，一个类身上挂不了两个不同 bus 的
     * {@code @Mod.EventBusSubscriber}。
     * </p>
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class CacheCleanup {

        private CacheCleanup() {
        }

        /**
         * 玩家登出时清除其范围查询缓存。
         * <p>不清的话，长期运行的服务器会攒下一堆早已下线玩家的条目。
         * 单条只有十几字节，但没有理由留着。</p>
         *
         * @param event 登出事件
         */
        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            NEARBY_CACHE.remove(event.getEntity().getUUID());
        }
    }
}
