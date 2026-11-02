package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(B2a)
 * <p>时间逆转附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "time_reversal",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST}
)
public class EnchantmentTimeReversal extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "time_reversal";

    /**
     * 时间逆转的冷却时间（tick）
     * <p>默认 6000，允许范围 20 ~ 144000。</p>
     */
    private static final EnchantmentValues.Handle REVERSAL_COOLDOWN =
            EnchantmentValues.define(VALUE_ID, "reversal_cooldown",
                    6000, 20, 144000);

    /**
     * 伤害累积窗口（tick）
     * <p>默认 100，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle ACCUMULATE_WINDOW =
            EnchantmentValues.define(VALUE_ID, "accumulate_window",
                    100, 1, 1200);

    /**
     * 按累积伤害回复生命的比例
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "heal_ratio",
                    0.25D, 0.0D, 5.0D);

    /**
     * 逆转状态的客户端同步序列号（v2.3 新增）。
     * <p>
     * 已占用：1~3 自定义火焰、4 隐身、5 猩红腐败、6 重力力场、7 冻伤、
     * 8 出血、9 切腹、10 睡眠、11 噩兆。故逆转取 12。
     * </p>
     * <p>
     * <b>定义在附魔类而非渲染器里</b>，与
     * {@code EnchantmentGravitas.GRAVITY_FIELD_SERIAL} 的做法一致：
     * 渲染器是 {@code @OnlyIn(Dist.CLIENT)} 的，服务端引用它会在
     * Mohist 等混合端引发类加载问题；反过来渲染器引用本类则完全安全。
     * </p>
     */
    public static final int TIME_REVERSAL_SERIAL = 12;

    private static final String REVERSAL_COOLDOWN_KEY = "time_reversal_cooldown";

    private static final String REVERSAL_STATE_KEY = "time_reversal_state";

    /**
     * 逆转期间累积伤害的数据键（值类型 {@code Float}）。
     * <p>
     * v2.3：{@code CarianStyleTimeReversalDisplay} 会用同一个字符串读取它来驱动 HUD。
     * 那边按本模组既有惯例<b>复制了一份字符串常量并加注释标注来源</b>
     * （{@code CarianStyleStackDisplays} 里全部十余个 key 都是这么做的），
     * <b>改动此处务必同步改那边</b>，否则 HUD 会静默地永远读不到值。
     * </p>
     */
    private static final String REVERSAL_DAMAGE_KEY = "time_reversal_damage";

    private static final int RECOLLECT_ENCHANTABILITY = 35;

    /**
     * 线程级重入保护标记。
     * <p>
     * 当本线程正在执行逆转反弹时置为 {@code true}。
     * 反弹使用原始伤害源（带攻击者实体），会再次进入 {@link #onLivingAttack}，
     * 若反弹目标同样处于逆转状态则会再次反弹，形成来回反弹。
     * 此标记在「反弹」步骤前拦截，确保反弹只发生一次；
     * 而逆转免疫（{@code setCanceled}）与伤害累积在标记拦截前已执行，正常效果完整保留。
     * </p>
     */
    private static final ThreadLocal<Boolean> PROCESSING_REFLECT = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /**
     * 正在逆转的实体。只是 {@link #isReversing} 的快速门槛：持续伤害每条每 tick 都要问一次，
     * 绝大多数时候这里是空的，不用每次拼字符串键去查数据管理器。是否逆转仍以 {@link #REVERSAL_STATE_KEY} 为准。
     */
    private static final Set<UUID> REVERSING = ConcurrentHashMap.newKeySet();

    public EnchantmentTimeReversal() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    /**
     * 实体是否正处于逆转状态
     *
     * @param entity 实体
     * @return 逆转中返回 true
     */
    public static boolean isReversing(@Nonnull EntityLivingBase entity) {
        // 桩：没有回溯，恒为 false（持续伤害、自损代价要调它）
        return false;
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isDeadEnchantment(ench)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    /**
     * 判断是否为死亡类附魔（互斥）
     *
     * @param ench 待检查的附魔
     * @return 是否为死亡类附魔
     */
    private boolean isDeadEnchantment(Enchantment ench) {
        return ench instanceof EnchantmentFullMoon || ench instanceof EnchantmentLivingCorpse;
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
