package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>卡利亚方阵附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(id = "carian_phalanx", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnumEnchantmentType.BOW, slots = {EntityEquipmentSlot.MAINHAND}, conflictsWith = {EnchantmentPyroxeneIce.class})
public class EnchantmentCarianPhalanx extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "carian_phalanx";

    /**
     * 环上的辉剑数量
     * <p>默认 9，允许范围 1 ~ 64。</p>
     */
    private static final EnchantmentValues.Handle BLADE_COUNT =
            EnchantmentValues.define(VALUE_ID, "blade_count",
                    9, 1, 64);

    /**
     * 扇形展开半径（格）
     * <p>默认 2.1，允许范围 0.5 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle FAN_RADIUS =
            EnchantmentValues.define(VALUE_ID, "fan_radius",
                    2.1D, 0.5D, 32.0D);

    /**
     * 第一把剑的发射延迟（tick）
     * <p>默认 45，允许范围 0 ~ 600。</p>
     */
    private static final EnchantmentValues.Handle BASE_DELAY =
            EnchantmentValues.define(VALUE_ID, "base_delay",
                    45, 0, 600);

    /**
     * 相邻两把剑的发射间隔（tick）
     * <p>默认 5，允许范围 0 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DELAY_STEP =
            EnchantmentValues.define(VALUE_ID, "delay_step",
                    5, 0, 120);

    /**
     * 每级的触发概率（百分比）
     * <p>默认 2.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle TRIGGER_CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "trigger_chance_per_level",
                    2.0D, 0.0D, 100.0D);

    /**
     * 扇形张角的一半（度）：整扇覆盖 2×该值，以正后方为中心左右展开。
     *
     * <h4>这个值直接决定「第一人称能不能瞥见剑」</h4>
     * <p>
     * 张角 θ 的剑，其方位偏离<b>视线方向</b>的角度是 {@code 180° - θ}。而 Minecraft 默认
     * FOV 70 指的是<b>垂直</b>视角，16:9 下换算出的水平半视角约 51°：
     * </p>
     * <pre>
     * θ = 58°   → 偏离视线 122°   完全在背后，第一人称一点都看不到
     * θ = 90°   → 偏离视线  90°   正侧方，仍在视野外
     * θ = 125°  → 偏离视线  55°   恰好压在余光边缘
     * θ = 135°  → 偏离视线  45°   明确可见，开始占视野
     * </pre>
     * <p>
     * 取 {@value} 后，9 把剑里<b>只有最外侧那一对</b>落在视野边缘、能瞥见一点冷蓝的闪，
     * 其余七把仍在身后与两侧。FOV 开得越大看到的越多（FOV 90 时水平半视角约 61°，
     * 外侧两对都会进来）。
     * </p>
     * <p>
     * <b>再往上调要谨慎：</b>超过 140° 中间那几把就会绕到身前，
     * 挡视线的观感会立刻盖过「余光有东西」的惊艳感。
     * </p>
     */
    private static final double FAN_HALF_SPREAD = 125.0;

    /** 扇形中心（正后方那把）离施法者脚底的高度（格） */
    private static final double FAN_HEIGHT_CENTER = 2.0;

    /**
     * 扇形两端相对中心的下沉量（格）：中间高两边低，形成向上拱起的弧。
     * <p>玩家眼高约 1.62 格，外侧剑落在 {@code 2.0 - 0.75 = 1.25} 格——略低于视线，
     * 正好是余光最自然的位置；抬到眼高反而会变成「有东西挡在脸边上」。</p>
     */
    private static final double FAN_HEIGHT_DROP = 0.75;

    public EnchantmentCarianPhalanx() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int l) {
        return (int) ((20 + (l - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
