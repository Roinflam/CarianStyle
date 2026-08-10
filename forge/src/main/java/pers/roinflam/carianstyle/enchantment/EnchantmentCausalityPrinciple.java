package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.network.AoeEffectPacket;
import pers.roinflam.carianstyle.utils.util.AoeHelper;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleEffects;

import java.util.List;

/**
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
 * <h3>崩溃链路（对应看门狗线程转储第 133 行）</h3>
 * <ol>
 *   <li>玩家受击 → LivingDamageEvent → 本附魔第 5 次累积触发 AOE 反击</li>
 *   <li>AOE 对周围实体 hurt → 每个被击中实体再走完整「受击 → 伤害」事件链</li>
 *   <li>若被击中实体也穿因果律护甲 → 继续累积计数 → 再次触发 AOE，呈指数级放大</li>
 *   <li>叠加 ReflectTrait 反弹 + celestial_core 每次攻击 addModifier，
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
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends EnchantmentBase {

    /** AOE 反击搜索半径硬上限（方块） */
    private static final int MAX_SEARCH_RADIUS = 10;

    /** 单次 AOE 反击最大命中目标数 */
    private static final int MAX_TARGETS = 20;

    /** 受击累积计数器键 */
    private static final String COUNTER_KEY = "causality_principle";

    /** 触发 AOE 反击所需的受击累积次数 */
    private static final int TRIGGER_COUNT = 5;

    /** AOE 反击触发冷却键 */
    private static final String COOLDOWN_KEY = "causality_principle_cooldown";

    /** AOE 反击触发冷却（tick）：1 秒（20 tick）最多触发一次 */
    private static final int TRIGGER_COOLDOWN = 20;

    /**
     * 本附魔专属的线程级重入保护。
     * <p>
     * AOE 反击对周围实体造成的二次伤害会再次进入 {@link #onLivingDamage}，
     * 此守卫在方法入口处直接拦截，防止因果律自身递归触发，
     * 进而避免与 ReflectTrait / celestial_core 形成伤害事件级联导致服务端崩溃。
     * </p>
     * <p>
     * <b>每个附魔必须持有自己独立的一份</b>，不同附魔之间互不影响
     * （因果律的守卫不应阻止学者盾的反伤）。
     * </p>
     */
    private static final AoeHelper.ReentrancyGuard AOE_GUARD = new AoeHelper.ReentrancyGuard();

    public EnchantmentCausalityPrinciple() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    /**
     * 受击事件监听：累积受击次数，每满 {@link #TRIGGER_COUNT} 次对周围敌人造成一次 AOE 反击。
     *
     * @param evt 生物受到伤害事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // 重入保护 —— 若当前线程正在执行因果律 AOE 反击，直接跳过，
        // 阻断因果律自身的伤害事件级联（核心崩溃修复点，详见类注释）
        if (AOE_GUARD.isActive()) {
            return;
        }

        if (evt.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // 怪物附魔触发开关（受击者视角，5 次受击触发 AOE 反击）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment causalityPrinciple = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCausalityPrinciple.class);
        if (causalityPrinciple == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(causalityPrinciple, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        final int effectiveLevel = totalLevel;

        int currentCount = EnchantmentDataManager.incrementCounter(COUNTER_KEY, victim.getUUID());

        if (currentCount >= TRIGGER_COUNT) {
            // 触发冷却判断。冷却中保持计数不重置，冷却结束后下次受击立即触发，
            // 保证 AOE 反击 1 秒最多触发一次
            if (EnchantmentDataManager.isOnCooldown(COOLDOWN_KEY, victim.getUUID())) {
                return;
            }

            EnchantmentDataManager.resetCounter(COUNTER_KEY, victim.getUUID());
            EnchantmentDataManager.setCooldown(COOLDOWN_KEY, victim.getUUID(), TRIGGER_COOLDOWN);

            int searchRadius = Math.min(effectiveLevel * 3, MAX_SEARCH_RADIUS);

            // ⭐ AOE 触发时播放一发因果律金紫六芒星法阵（约 1100ms）。
            // 特效半径取实际作用半径 searchRadius，保证「看到多大就打多大」。
            // 纯服务端发包，不生成实体、不触发任何事件，
            // 不影响下方伤害逻辑与两层崩服防护。
            if (victim.level() instanceof ServerLevel serverLevel) {
                CarianStyleEffects.causality(
                        serverLevel,
                        victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        searchRadius
                );
            }

            List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    victim,
                    searchRadius,
                    entity -> !entity.equals(victim)
            );

            float damage = evt.getAmount() * effectiveLevel * 0.75f;

            // 在守卫保护下执行 AOE 反击，确保 target.hurt 触发的二次伤害事件
            // 不会再次进入本方法形成级联；ReentrancyGuard 内部用 try-finally 复位标记，
            // 即使 hurt 抛出异常也不会让标记滞留、导致因果律永久失效
            AOE_GUARD.run(() -> {
                int hitCount = 0;
                for (LivingEntity target : targets) {
                    if (hitCount >= MAX_TARGETS) {
                        break;
                    }
                    target.hurt(victim.damageSources().mobAttack(victim), damage);
                    hitCount++;
                }
            });
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
