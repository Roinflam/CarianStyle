package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
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
 * PORT-STUB(E1)
 * <p>重力附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "gravitas",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentGravitas extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "gravitas";

    /**
     * 每级的力场持续秒数
     * <p>默认 4，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle ACTIVE_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "active_seconds_per_level", 4, 1, 120);

    /**
     * 命中时每级施加重力效果的持续秒数
     * <p>默认 2，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "effect_seconds_per_level", 2, 1, 120);

    /**
     * 命中效果的等级基数（最终等级 = 基数 + 附魔等级 × 每级增量 − 1）
     * <p>默认 10，允许范围 1 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle AMPLIFIER_BASE =
            EnchantmentValues.define(VALUE_ID, "amplifier_base", 10, 1, 127);

    /**
     * 命中效果每级增加的等级
     * <p>默认 4，允许范围 0 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle AMPLIFIER_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "amplifier_per_level", 4, 0, 32);

    /**
     * 力场光环对周围生物施加的效果等级
     * <p>默认 9，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle AURA_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "aura_amplifier", 9, 0, 127);

    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";

    /**
     * 重力力场的作用半径（格）。周围生物范围判定（{@link #onPlayerTick}）与客户端范围圈视觉
     * （{@code GravitasDistortionRenderer}）必须保持一致，故抽成公开常量供渲染器引用，
     * 避免两处各自写死 12 导致以后改动漏改一处。
     */
    public static final EnchantmentValues.Handle FIELD_RADIUS =
            EnchantmentValues.defineShared(VALUE_ID, "field_radius", 12, 1, 64);

    /**
     * 重力力场的客户端同步序列号，供 {@code GravitasDistortionRenderer} 判断「谁正在施放力场」，
     * 从而绘制以施法者为中心、半径 {@link #FIELD_RADIUS} 格的地面范围圈。序列号 1~3 为自定义火焰、
     * 4 为隐身、5 为猩红腐败（见 {@code ScarletRotSyncHandler}），本附魔取 6，避免冲突。
     */
    public static final int GRAVITY_FIELD_SERIAL = 6;

    /**
     * 力场持有者集合：记录当前「已同步给客户端」的玩家 UUID。
     * <p>{@link EnchantmentDataManager} 只提供「是否在冷却中」的瞬时查询、没有到期回调，
     * 故力场的开启/关闭边沿由本集合自行检测：力场刚激活时（此前不在集合中）才调用一次
     * {@link ClientSyncEffectManager#addEntity} 广播；力场结束时（不再在冷却中但仍在集合中）
     * 才调用一次 {@link ClientSyncEffectManager#removeEntity}，避免每 tick 重复处理。
     * 仅服务端 tick 线程访问。</p>
     * <p>v2.4：新增登出清理路径，见类注释。</p>
     */
    private static final Set<UUID> ACTIVE_FIELD_HOLDERS = ConcurrentHashMap.newKeySet();

    public EnchantmentGravitas() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.KNOCKBACK) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
