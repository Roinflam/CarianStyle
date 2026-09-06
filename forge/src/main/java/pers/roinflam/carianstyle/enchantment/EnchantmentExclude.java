package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.List;

/**
 * 排斥附魔
 * <p>
 * 护腿附魔，受击时范围击退
 * 受到攻击时击退周围所有敌人
 * 范围 = 5 + (等级 - 1) × 0.75 格
 * </p>
 *
 * <h3>特效：贴地的冲击环</h3>
 * <p>
 * 触发点广播 {@link AoeEffectPacket#TYPE_REPULSION}——
 * 两道发光环从中心猛烈外推扩张并快速淡出的短促冲击波（约 520ms）。
 * </p>
 * <p>
 * <b>中心 Y 必须取脚底</b>（由 {@link CarianStyleEffects#repulsion} 的实体重载自动处理）。
 * 早期版本传的是半身高 {@code getY() + bbHeight * 0.5}——那个取法对「贴身球状爆发」
 * 是合理的，但本演出实际是<b>两道水平地面光环</b>
 * （见 {@code AoeEffectRenderer.drawRepulsion}，由 {@code glowRing} 绘制、y 固定），
 * 于是环被画在腰部、悬在半空，既不符合「以自身为中心把周围推开」的地面冲击语义，
 * 也和模组内其余地面法阵（因果律、冻结地震、光环等一律贴地）不一致。
 * </p>
 * <p>
 * 渲染器侧另有 {@code Y_OFFSET = 0.02f} 的离地微抬用于避免与地形 z-fighting，
 * 调用方无需额外补偿。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "exclude",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_LEGS,
        slots = {EquipmentSlot.LEGS}
)
@Mod.EventBusSubscriber
public class EnchantmentExclude extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.exclude.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "exclude";

    /**
     * AOE 搜索半径上限（格）
     * <p>默认 10.0，允许范围 1.0 ~ 64.0。</p>
     */
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS =
            EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10.0D, 1.0D, 64.0D);

    /**
     * 单次触发最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 基础作用半径（格）
     * <p>默认 5.0，允许范围 0.5 ~ 64.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_RANGE =
            EnchantmentValues.define(VALUE_ID, "base_range",
                    5.0D, 0.5D, 64.0D);

    /**
     * 每超过一级增加的作用半径（格）
     * <p>默认 0.75，允许范围 0.0 ~ 16.0。</p>
     */
    private static final EnchantmentValues.Handle RANGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "range_per_level",
                    0.75D, 0.0D, 16.0D);

    /**
     * 排斥的击退强度
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.5D, 0.0D, 5.0D);

    public EnchantmentExclude() {
        super(EnchantmentCategory.ARMOR_LEGS, new EquipmentSlot[]{EquipmentSlot.LEGS});
    }

    /**
     * 受击事件监听：范围击退周围所有生物。
     *
     * @param evt 生物受到攻击事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment exclude = EnchantmentRegistry.getEnchantmentByClass(EnchantmentExclude.class);

        if (exclude == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(exclude, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }

        if (totalLevel <= 0) {
            return;
        }

        double range = BASE_RANGE.get() + (totalLevel - 1) * RANGE_PER_LEVEL.get();
        double searchRadius = Math.min(range, MAX_SEARCH_RADIUS.get());

        // ⭐ 受击触发范围击退时播放一发排斥冲击波（双环猛烈外推、约 520ms）。
        // 传实体重载 → 内部取脚底坐标，冲击环贴地（详见类注释）。
        // 纯服务端发包，不生成实体、不触发任何事件，不影响下方击退逻辑。
        if (victim.level() instanceof ServerLevel serverLevel) {
            CarianStyleEffects.repulsion(serverLevel, victim);
        }

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                searchRadius,
                entity -> !entity.equals(victim)
        );

        int hitCount = 0;
        for (LivingEntity target : targets) {
            if (hitCount >= MAX_TARGETS.getInt()) {
                break;
            }
            double x = victim.getX() - target.getX();
            double z = victim.getZ() - target.getZ();
            target.knockback((float) KNOCKBACK_STRENGTH.get(), x, z);
            hitCount++;
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
