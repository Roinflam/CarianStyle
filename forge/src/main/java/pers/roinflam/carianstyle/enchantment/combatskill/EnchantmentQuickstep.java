package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 快步附魔
 * <p>
 * 血量越低速度越快
 * 速度等级 = (损失血量百分比) / 5 × 附魔总等级
 * </p>
 * <p>
 * 性能优化记录：
 * - 每4tick检测一次（而非每tick），给予6tick的duration确保无缝衔接
 * - 提前检查附魔等级，避免无附魔玩家执行后续计算
 * - 速度等级为0时跳过apply调用
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "quickstep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_FEET,
        slots = {EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentQuickstep extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.quickstep.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "quickstep";

    /**
     * 速度重算间隔（tick）；调大可降低服务端开销
     * <p>默认 4，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle CHECK_INTERVAL_TICKS =
            EnchantmentValues.define(VALUE_ID, "check_interval_ticks",
                    4, 1, 100);

    /**
     * 速度等级除数：等级 = 已损失生命百分比 × 100 ÷ 本值 × 附魔等级
     * <p>默认 5.0，允许范围 1.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle SPEED_DIVISOR =
            EnchantmentValues.define(VALUE_ID, "speed_divisor",
                    5.0D, 1.0D, 100.0D);

    /**
     * 每次施加的速度持续时间（tick）
     * <p>默认 6，允许范围 1 ~ 200。</p>
     * <p>
     * <b>⚠ 必须大于 {@code check_interval_ticks}</b>，否则两次结算之间会出现速度断档，
     * 玩家会感到一顿一顿的。默认值 6 覆盖默认间隔 4 正是这个道理；
     * 若把间隔调大，请同步把本值调到比它更大。
     * </p>
     */
    private static final EnchantmentValues.Handle SPEED_DURATION_TICKS =
            EnchantmentValues.define(VALUE_ID, "speed_duration_ticks",
                    6, 1, 200);

    public EnchantmentQuickstep() {
        super(EnchantmentCategory.ARMOR_FEET, new EquipmentSlot[]{EquipmentSlot.FEET});
    }

    /**
     * 每4tick检测一次血量比例并调整速度
     * <p>
     * 优化：从每tick检测改为每4tick检测，duration给6tick确保覆盖间隔
     * 血量变化不需要逐tick精确跟踪，4tick的延迟对玩家体验几乎无感知
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        // 优化：每4tick检测一次
        if (evt.player.tickCount % CHECK_INTERVAL_TICKS.getInt() != 0) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        Enchantment quickstep = EnchantmentRegistry.getEnchantmentByClass(EnchantmentQuickstep.class);
        if (quickstep == null) {
            return;
        }

        // v-cache：走中央装备缓存
        int totalLevel = EnchantmentEventHandler.armorTotal(player, quickstep);

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - player.getHealth() / player.getMaxHealth();
        int speedLevel = (int) (missingHealthPercent * 100 / SPEED_DIVISOR.get() * totalLevel);

        // 优化：速度等级为0时不创建实例
        if (speedLevel > 0) {
            // 优化：duration给6tick覆盖4tick间隔，确保效果无缝
            DynamicAttributeManager.apply(player,
                    DynamicAttributes.SPEED_BOOST.createInstance(SPEED_DURATION_TICKS.getInt(), speedLevel - 1));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
