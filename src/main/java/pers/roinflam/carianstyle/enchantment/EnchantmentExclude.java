package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
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
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
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
@Deprecated
public class EnchantmentExclude extends EnchantmentBase {
    private static final String VALUE_ID = "exclude";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10.0D, 1.0D, 64.0D);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle BASE_RANGE = EnchantmentValues.define(VALUE_ID, "base_range",
                    5.0D, 0.5D, 64.0D);
    private static final EnchantmentValues.Handle RANGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "range_per_level",
                    0.75D, 0.0D, 16.0D);
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH = EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.5D, 0.0D, 5.0D);
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

