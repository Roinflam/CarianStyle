package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

import java.util.List;

/**
 * 因果律附魔
 * <p>v2.2：LivingDamage 受击者视角入口接入怪物附魔触发开关</p>
 * <p>v2.3：新增 ThreadLocal 重入保护，修复因果律 AOE 反击造成的二次伤害再次触发自身、
 * 并与 l2hostility ReflectTrait（反弹）/ celestial_core（属性 modifier 累积）形成
 * 自我喂养式伤害事件级联，导致单 tick 耗时突破 Spigot Watchdog 阈值而崩服的问题。</p>
 * <p>v2.4：新增 1 秒（20 tick）触发冷却，限制 AOE 反击的最大触发频率，
 * 进一步抑制密集战斗下的事件压力与数值爆发。</p>
 * <p>v2.5：新增 AOE 触发时的能量冲击波粒子视觉（纯服务端 sendParticles 广播，
 * 不新增网络包，不触碰任何伤害与崩服修复逻辑）。</p>
 *
 * <h3>崩溃链路（v2.3 修复前，对应看门狗线程转储第 133 行）</h3>
 * <ol>
 *   <li>玩家受击 → LivingDamageEvent → 本附魔第 5 次累积触发 AOE 反击</li>
 *   <li>AOE 对周围实体 hurt → 每个被击中实体再走完整「受击 → 伤害」事件链</li>
 *   <li>若被击中实体也穿因果律护甲 → 继续累积计数 → 再次触发 AOE，呈指数级放大</li>
 *   <li>叠加 ReflectTrait 反弹 + celestial_core 每次攻击 addModifier，
 *       单 tick 内伤害事件爆炸式增长，最终卡死在 AttributeInstance 的 HashSet.add</li>
 * </ol>
 *
 * <h3>双重防护机制</h3>
 * <ul>
 *   <li>{@link #PROCESSING_AOE}：线程级重入保护，阻断因果律自身的同步伤害事件级联。</li>
 *   <li>{@link #COOLDOWN_KEY} + {@link #TRIGGER_COOLDOWN}：触发冷却，1 秒最多触发一次 AOE 反击。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 2.5
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
     * 线程级重入保护标记。
     * <p>
     * 当本线程正在执行因果律 AOE 反击时置为 {@code true}。
     * AOE 反击对周围实体造成的二次伤害会再次进入 {@link #onLivingDamage}，
     * 此标记用于在方法入口处直接拦截，防止因果律自身递归触发，
     * 进而避免与 ReflectTrait / celestial_core 形成伤害事件级联导致服务端崩溃。
     * </p>
     */
    private static final ThreadLocal<Boolean> PROCESSING_AOE = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentCausalityPrinciple() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    /**
     * 受击事件监听：累积受击次数，每满 {@link #TRIGGER_COUNT} 次对周围敌人造成一次 AOE 反击。
     * <p>v2.3：入口处增加 {@link #PROCESSING_AOE} 重入保护，AOE 反击产生的二次伤害不再触发本逻辑。</p>
     * <p>v2.4：触发 AOE 前增加 {@link #COOLDOWN_KEY} 冷却判断，1 秒最多触发一次。</p>
     *
     * @param evt 生物受到伤害事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        // ⭐ v2.3：重入保护 —— 若当前线程正在执行因果律 AOE 反击，直接跳过，
        // 阻断因果律自身的伤害事件级联（核心崩溃修复点）
        if (PROCESSING_AOE.get()) {
            return;
        }

        if (evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受击者视角，5 次受击触发 AOE 反击）
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
            // ⭐ v2.4：触发冷却判断。冷却中保持计数不重置，冷却结束后下次受击立即触发，
            // 保证 AOE 反击 1 秒最多触发一次
            if (EnchantmentDataManager.isOnCooldown(COOLDOWN_KEY, victim.getUUID())) {
                return;
            }

            EnchantmentDataManager.resetCounter(COUNTER_KEY, victim.getUUID());
            EnchantmentDataManager.setCooldown(COOLDOWN_KEY, victim.getUUID(), TRIGGER_COOLDOWN);

            int searchRadius = Math.min(effectiveLevel * 3, MAX_SEARCH_RADIUS);

            // ⭐ v2.5 视觉：AOE 触发时在受击者周围发射一圈能量冲击波粒子。
            // 纯服务端 sendParticles 广播，粒子不触发任何事件，不影响伤害逻辑与上方崩服修复
            if (victim.level() instanceof ServerLevel serverLevel) {
                CarianStyleBurstParticles.shockwaveRing(
                        serverLevel,
                        victim.getX(), victim.getY() + 0.1, victim.getZ(),
                        searchRadius, 28, ParticleTypes.END_ROD
                );
            }

            List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    victim,
                    searchRadius,
                    entity -> !entity.equals(victim)
            );

            float damage = evt.getAmount() * effectiveLevel * 0.75f;

            // ⭐ v2.3：进入 AOE 反击前置位重入标记，确保 target.hurt 触发的二次伤害事件
            // 不会再次进入本方法形成级联；使用 try-finally 保证即使 hurt 抛出异常，
            // 标记也能被正确复位，避免因果律因标记滞留而永久失效
            PROCESSING_AOE.set(Boolean.TRUE);
            try {
                int hitCount = 0;
                for (LivingEntity target : targets) {
                    if (hitCount >= MAX_TARGETS) {
                        break;
                    }
                    target.hurt(victim.damageSources().mobAttack(victim), damage);
                    hitCount++;
                }
            } finally {
                PROCESSING_AOE.set(Boolean.FALSE);
            }
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
