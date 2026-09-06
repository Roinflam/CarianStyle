package pers.roinflam.carianstyle.visual.toggle;

import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * 可独立开关的特效类型。
 * <p>
 * 一个枚举项对应一个渲染器（或一组同源渲染器）。接入方式是在渲染器的
 * {@code @SubscribeEvent} 方法开头加一行早退：
 * </p>
 * <pre>
 * if (!VisualToggle.isEnabled(VisualEffectType.HEMORRHAGE)) return;
 * </pre>
 * <p>
 * 这一行放在<b>所有计算之前</b>——包括 {@code SharedEntityQuery} 调用之前——
 * 才能真正省掉开销；放在循环里只能省掉绘制，实体遍历照跑。
 * </p>
 *
 * <h3>分组的意义</h3>
 * <p>
 * {@link Category} 只影响界面上的排列，不影响开关逻辑。分组是为了让玩家
 * 能按「我嫌状态效果太花」或「我嫌战技太闪」这种粒度快速关一整类，
 * 而不用在 20 多个开关里逐个找。
 * </p>
 *
 * <h3>为什么不做成一个总开关</h3>
 * <p>
 * 本模组的特效同时承担<b>信息</b>职能——出血/冻伤的视觉是玩家判断敌人状态的依据，
 * 战技范围圈是判断打不打得到的依据。一刀切关掉会让 PVP 变成盲打。
 * 因此默认全开，且把「纯观赏」与「带信息」的效果分在不同组，
 * 界面上对后者标注提示，让玩家知道关掉的代价。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public enum VisualEffectType {

    // ==================== 状态效果（带信息职能） ====================

    /** 出血飙血（HemorrhageBloodRenderer） */
    HEMORRHAGE("hemorrhage", Category.STATUS, true),
    /** 猩红腐败雾（ScarletRotMistRenderer） */
    SCARLET_ROT("scarlet_rot", Category.STATUS, true),
    /** 冻伤冰雾（FrostbiteMistRenderer） */
    FROSTBITE("frostbite", Category.STATUS, true),
    /** 切割（IncisionRenderer） */
    INCISION("incision", Category.STATUS, true),
    /** 睡眠（SleepRenderer） */
    SLEEP("sleep", Category.STATUS, true),
    /** 不祥预感（BadOmenRenderer） */
    BAD_OMEN("bad_omen", Category.STATUS, true),
    /** 重力压制扭曲（GravitasDistortionRenderer） */
    GRAVITAS("gravitas", Category.STATUS, true),
    /** 黄金树祝福（GoldenTreeBlessingRenderer） */
    GOLDEN_TREE("golden_tree", Category.STATUS, true),

    // ==================== 战技与范围指示（强信息职能） ====================

    /** 战技爆发（CombatArtBurstRenderer） */
    COMBAT_ART_BURST("combat_art_burst", Category.COMBAT_ART, true),
    /** 战技通用特效（CombatArtEffectRenderer） */
    COMBAT_ART_EFFECT("combat_art_effect", Category.COMBAT_ART, true),
    /** 战技附加特效（CombatArtExtraRenderer） */
    COMBAT_ART_EXTRA("combat_art_extra", Category.COMBAT_ART, false),
    /** 硬箭范围指示（HardArrowRangeRenderer） */
    HARD_ARROW_RANGE("hard_arrow_range", Category.COMBAT_ART, true),
    /** 猎犬连击（WaterfowlFlurryRenderer） */
    WATERFOWL_FLURRY("waterfowl_flurry", Category.COMBAT_ART, false),
    /** 卡利亚反击（CarianRetaliationRenderer） */
    CARIAN_RETALIATION("carian_retaliation", Category.COMBAT_ART, true),
    /** 盾墙（ShieldWardRenderer） */
    SHIELD_WARD("shield_ward", Category.COMBAT_ART, true),

    // ==================== 范围与法阵 ====================

    /** 定点 / 跟随 AOE（AoeEffectRenderer） */
    AOE("aoe", Category.AREA, true),
    /** 光环地面法阵（AuraGroundRenderer） */
    AURA_GROUND("aura_ground", Category.AREA, true),
    /** 辉剑投射物特效（GlintbladesEffectRenderer） */
    GLINTBLADES("glintblades", Category.AREA, false),

    // ==================== 大招演出（纯观赏为主） ====================

    /** 灾祸（CalamityRenderer） */
    CALAMITY("calamity", Category.SPECTACLE, false),
    /** 暗月（DarkMoonRenderer） */
    DARK_MOON("dark_moon", Category.SPECTACLE, false),
    /** 妲德凯尔之殇（DaedicarWoeRenderer） */
    DAEDICAR_WOE("daedicar_woe", Category.SPECTACLE, false),
    /** 夏布利利之嚎（HowlShabririRenderer） */
    HOWL_SHABRIRI("howl_shabriri", Category.SPECTACLE, false),
    /** 时间逆转（TimeReversalRenderer） */
    TIME_REVERSAL("time_reversal", Category.SPECTACLE, false),

    // ==================== 界面 ====================

    /** 叠层 HUD（StackHudOverlay） */
    STACK_HUD("stack_hud", Category.HUD, true);

    /** 配置键与语言键的后缀 */
    private final String key;

    /** 所属分组 */
    private final Category category;

    /** 是否承担信息职能（关闭会影响战斗判断，界面上给出提示） */
    private final boolean informational;

    /** 名称语言键，构造期合成避免运行期拼接 */
    private final String nameKey;

    /** 说明语言键 */
    private final String descKey;

    VisualEffectType(String key, Category category, boolean informational) {
        this.key = key;
        this.category = category;
        this.informational = informational;
        this.nameKey = "carianstyle.visual." + key;
        this.descKey = this.nameKey + ".desc";
    }

    /**
     * @return 配置文件中使用的键
     */
    @Nonnull
    public String getKey() {
        return key;
    }

    /**
     * @return 所属分组
     */
    @Nonnull
    public Category getCategory() {
        return category;
    }

    /**
     * @return 是否承担信息职能
     */
    public boolean isInformational() {
        return informational;
    }

    /**
     * @return 显示名
     */
    @Nonnull
    public Component getDisplayName() {
        return Component.translatable(nameKey);
    }

    /**
     * @return 说明文字
     */
    @Nonnull
    public Component getDescription() {
        return Component.translatable(descKey);
    }

    /**
     * 按配置键反查枚举项。
     *
     * @param key 配置键
     * @return 对应枚举项；不存在时返回 null
     */
    @Nullable
    public static VisualEffectType byKey(@Nullable String key) {
        if (key == null) {
            return null;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        for (VisualEffectType type : values()) {
            if (type.key.equals(lower)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 特效分组，仅用于界面排列。
     */
    public enum Category {
        /** 状态效果 */
        STATUS("carianstyle.visual.category.status"),
        /** 战技 */
        COMBAT_ART("carianstyle.visual.category.combat_art"),
        /** 范围与法阵 */
        AREA("carianstyle.visual.category.area"),
        /** 大招演出 */
        SPECTACLE("carianstyle.visual.category.spectacle"),
        /** 界面 */
        HUD("carianstyle.visual.category.hud");

        private final String langKey;

        Category(String langKey) {
            this.langKey = langKey;
        }

        /**
         * @return 分组显示名
         */
        @Nonnull
        public Component getDisplayName() {
            return Component.translatable(langKey);
        }
    }
}
