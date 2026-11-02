package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
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
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.AoeHelper;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;
import java.util.List;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
 * 因果律附魔
 * <p>
 * 护甲附魔。每累积受击 {@link #TRIGGER_COUNT} 次，对周围敌人发动一次 AOE 反击，
 * 伤害按本次受击量 × 等级计算。
 * </p>
 *
 * <h3>特效</h3>
 * <p>
 * 触发点广播 {@link AoeEffectPacket#TYPE_CAUSALITY}——地面金色六芒星法阵（旋转）
 * + 内六边形 + 外发光环 + 紫色「因果之线」放射抽射 + 六芒星顶点火花，约 1100ms。
 * 半径与实际作用半径一致；环分段数由渲染器按半径自算（夹取 36~72），调用方不用管。
 * </p>
 *
 * <h3>为什么这个附魔有两层防护</h3>
 * <p>
 * 本附魔曾导致过一次实际崩服，两层防护缺一不可，<b>改动时请勿削减</b>：
 * </p>
 * <ul>
 *   <li>{@link #AOE_GUARD}：线程级重入保护，阻断因果律自身的同步伤害事件级联。</li>
 *   <li>{@link #COOLDOWN_KEY} + {@link #TRIGGER_COOLDOWN}：触发冷却，1 秒最多触发一次 AOE 反击。</li>
 * </ul>
 * <p>
 * 重入保护使用公共实现 {@link AoeHelper.ReentrancyGuard}（因果律、时间逆转、学者盾
 * 曾各手写一份完全相同的 ThreadLocal 逻辑）。其 {@code run()} 内部用 try-finally
 * 复位标记，即使 {@code hurt} 抛异常也不会让标记滞留、导致本附魔永久失效——
 * 这正是手写版本最容易漏掉的地方。
 * </p>
 *
 * <h3>崩溃链路</h3>
 * <ol>
 *   <li>玩家受击 → LivingDamageEvent → 本附魔第 5 次累积触发 AOE 反击</li>
 *   <li>AOE 对周围实体 hurt → 每个被击中实体再走完整「受击 → 伤害」事件链</li>
 *   <li>若被击中实体也穿因果律护甲 → 继续累积计数 → 再次触发 AOE，呈指数级放大</li>
 *   <li>再叠加其它模组的反伤与「每次攻击都 addModifier」的机制，
 *       单 tick 内伤害事件爆炸式增长，最终卡死在 AttributeInstance 的 HashSet.add</li>
 * </ol>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "causality_principle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 10,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentCausalityPrinciple extends EnchantmentBase {
    private static final String VALUE_ID = "causality_principle";
    private static final EnchantmentValues.Handle MAX_SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "max_search_radius",
                    10, 1, 64);
    private static final EnchantmentValues.Handle MAX_TARGETS = EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);
    private static final EnchantmentValues.Handle TRIGGER_COUNT = EnchantmentValues.define(VALUE_ID, "trigger_count",
                    5, 1, 100);
    private static final EnchantmentValues.Handle TRIGGER_COOLDOWN = EnchantmentValues.define(VALUE_ID, "trigger_cooldown",
                    20, 0, 1200);
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.75D, 0.0D, 10.0D);
    private static final String COUNTER_KEY = "causality_principle";
    private static final String COOLDOWN_KEY = "causality_principle_cooldown";
    private static final AoeHelper.ReentrancyGuard AOE_GUARD = new AoeHelper.ReentrancyGuard();
    public EnchantmentCausalityPrinciple() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }
}

