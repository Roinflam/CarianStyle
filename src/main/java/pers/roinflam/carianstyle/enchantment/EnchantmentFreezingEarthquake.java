package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.List;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
 * 冻结地震附魔
 * <p>
 * 护甲附魔。单次受击达到 25% 最大生命值时，将周围站在地面的敌人击飞并施加冻伤。
 * </p>
 *
 * <h3>特效：一次触发只发一发包</h3>
 * <p>
 * 触发点广播 {@link AoeEffectPacket#TYPE_FROST_QUAKE}
 * （放射地裂 + 霜环外滚 + 中心冰花），半径与实际作用半径一致。
 * </p>
 * <p>
 * <b>这里踩过一个坑，值得留档。</b>早期版本写成了两行：先调
 * {@code shockwaveRing(..., ParticleTypes.SNOWFLAKE)}，
 * 再手动 {@code VisualNetwork.sendToNearby(new AoeEffectPacket(TYPE_FROST_QUAKE, ...))}，
 * 注释解释为「原版雪花粒子环 + 自绘几何，二者叠加」。
 * 但那个前提当时就已不成立——特效系统早已改为纯自绘发包，
 * {@code ParticleTypes.*} 只是「选哪套演出」的标识符，不会生成任何原版粒子。
 * 于是两行的真实效果是<b>把同一个 FROST_QUAKE 包发了两遍</b>。
 * </p>
 * <p>
 * 客户端 {@code AoeEffectManager} 只对 {@code TYPE_RED_LIGHTNING} 做同位置合并，
 * FROST_QUAKE 不在合并之列，两发包各自 {@code spawn} 出独立实例，
 * 在同一坐标、同一时刻、同一半径完全重叠：顶点量翻倍、
 * 叠加混合后亮度明显偏浓（每层 alpha 被叠加两次），
 * 还额外占用 {@code MAX_ACTIVE}(64) 的存活特效名额。
 * </p>
 * <p>
 * 现调用 {@link CarianStyleEffects#frostQuake}，方法名即语义，
 * 不再依赖任何需要背诵的对应关系。
 * <b>以后加特效时请守住这条：一次触发只发一发包。</b>
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "freezing_earthquake",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 15,
        levelMultiplier = 15
)
@Deprecated
public class EnchantmentFreezingEarthquake extends EnchantmentBase {
    private static final String VALUE_ID = "freezing_earthquake";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);
    private static final EnchantmentValues.Handle TRIGGER_HEALTH_RATIO = EnchantmentValues.define(VALUE_ID, "trigger_health_ratio",
                    0.25D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle BASE_RANGE = EnchantmentValues.define(VALUE_ID, "base_range",
                    3, 1, 64);
    private static final EnchantmentValues.Handle RANGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "range_per_level",
                    2, 0, 16);
    private static final EnchantmentValues.Handle SLOW_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "slow_seconds_per_level",
                    5, 1, 120);
    public EnchantmentFreezingEarthquake() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }
}

