package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.List;

/**
 * 箭步回旋斩附魔
 * <p>
 * 冲刺攻击时，玩家快速转一圈（360度旋转）
 * 对自身周围3格的所有敌人（包括被直接攻击的目标）造成额外伤害
 * 伤害 = 本次攻击伤害 + 10% × 等级
 * 不会伤害队友
 * </p>
 * <p>
 * 修复记录：
 * - 修复直接目标可能被增加两次伤害的bug，简化逻辑为：
 *   直接目标通过ctx增伤，周围其他敌人通过hurt造成伤害
 * </p>
 *
 * <h3>性能安全上限（v2.2 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_TARGETS}：单次冲刺斩最大命中目标数上限。
 *       原逻辑对 3 格内所有敌人无上限 hurt，且每次 hurt 使用带攻击者实体的
 *       mobAttack 伤害源，会触发每个目标各自的受击类附魔（如因果律累积、各类反伤）。
 *       在密集怪物场景（村民聚集、刷怪塔）下可能一次攻击触发大量伤害事件链，
 *       故增加命中数量硬上限封顶。</li>
 * </ul>
 *
 * <p>注意：本次仅新增命中数量上限，旋转动画、增伤公式、目标筛选逻辑均保持不变；
 * 3 格范围内常规战斗几乎不会超过上限，对正常玩法无可观察影响。</p>
 *
 * <h3>v2.3 视觉</h3>
 * <p>触发瞬间广播一发自绘环形刀光特效
 * （{@link CarianStyleCombatArtEffects#spinSlash}——绕自身扫过 360° 的银白刀锋 + 琥珀扬尘环）。
 * 此前玩家虽然真的转了一圈（{@link #performSpinAnimation}），但没有任何刀光，
 * 旁观者只能看到一个人原地转圈、周围的怪莫名其妙掉血。</p>
 * <p><b>视觉与判定严格对齐：</b>特效半径取
 * {@link CarianStyleCombatArtEffects#spinSlash(ServerLevel, LivingEntity)} 的默认值 3.0 格，
 * 与下方 {@code EntityUtil.getNearbyEntities(..., 3, ...)} 的实际 AOE 半径一致——
 * 刀光扫到哪里就打到哪里，玩家可以据此走位。<b>若将来调整了 AOE 半径，
 * 务必改用带 radius 的重载同步传入新值</b>，否则视觉会骗人。</p>
 * <p>本次改动仅新增视觉，机制完全未动：旋转动画、增伤公式、目标筛选、命中上限全部保持原样；
 * 特效为纯服务端广播，不生成实体、不触发任何事件。触发条件是「冲刺攻击」，
 * 频率天然受控，不会刷屏。</p>
 *
 * <h3>v2.4：AOE 重入连锁 + 满蓄力门槛</h3>
 * <p><b>问题一：AOE 会把自己再触发一遍。</b>周围目标挨的是 {@code mobAttack(attacker)}，
 * 直接实体就是攻击者本人，于是每个 AOE 目标的 LivingDamageEvent 都会重新分发到本附魔；
 * 而原版要等 {@code hurt} 返回之后才取消疾跑，嵌套那一层里攻击者仍在疾跑，条件全部满足。
 * 结果是沿着怪群深度优先地一层套一层：每层再对周围最多 {@link #MAX_TARGETS} 个目标各 hurt 一次，
 * 伤害每层再乘 {@code 1 + 10% × 等级}，旋转任务和刀光也每层各放一次，
 * 身边挤着 12 只僵尸时，一下冲刺斩实测打出 1600 多次 LivingAttackEvent（命中数量上限只管一层，管不住层数）。</p>
 * <p><b>修复：</b>AOE 循环前后置位 / 清除 {@link #IN_SWEEP}（try/finally，异常也会清掉），
 * 进入本方法时已置位就直接返回。AOE 目标照常挨这一下，只是不再从它身上再放一圈。</p>
 * <p><b>问题二：连点刷 AOE。</b>武器不带击退附魔时，原版低蓄力命中不会打断疾跑，疾跑中连点每一下都放一圈 AOE、
 * 再拉一次旋转。现在玩家必须满蓄力（{@link EnchantmentBase#isFullyCharged}）才触发，
 * 未满蓄力就当没有这个附魔；怪物持有者不受影响。</p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@AutoRegisterEnchantment(
        id = "stamp_sweep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentStampSweep extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.stamp_sweep.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "stamp_sweep";

    /**
     * 单次横扫的最大命中目标数
     * <p>默认 20，允许范围 1 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle MAX_TARGETS =
            EnchantmentValues.define(VALUE_ID, "max_targets",
                    20, 1, 200);

    /**
     * 横扫半径（格）
     * <p>默认 3.0，允许范围 1.0 ~ 32.0。</p>
     */
    private static final EnchantmentValues.Handle SWEEP_RADIUS =
            EnchantmentValues.define(VALUE_ID, "sweep_radius",
                    3.0D, 1.0D, 32.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级的横扫额外伤害倍率
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.1D, 0.0D, 5.0D);


    /**
     * AOE 作用半径（格）。
     * <p>v2.3：抽为常量，供伤害判定与视觉特效共用，避免两处各自写死导致以后改动漏改一处
     * （视觉与判定不一致会让玩家误判走位）。</p>
     */

    /**
     * 正在结算 AOE 时置位（v2.4）。
     * <p>AOE 循环里的 {@code target.hurt} 会同步触发新的 LivingDamageEvent 并回到本附魔，
     * 置位期间直接放行，保证一次冲刺斩只放一圈。只在服务端线程读写，用 ThreadLocal 是为了不和别的线程互相干扰。</p>
     */
    private static final ThreadLocal<Boolean> IN_SWEEP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentStampSweep() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发：冲刺状态下对周围敌人造成范围伤害并旋转视角
     */
    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        // ⭐ v2.4：AOE 自己打出的伤害会再次进入这里，正在结算 AOE 时直接放行（详见类注释）
        if (IN_SWEEP.get()) {
            return;
        }

        LivingEntity attacker = ctx.getHolder();
        LivingEntity directTarget = ctx.getVictim();

        if (directTarget == null) {
            return;
        }

        // 必须在冲刺状态
        if (!attacker.isSprinting()) {
            return;
        }

        // ⭐ v2.4：武器不带击退附魔时，原版低蓄力命中不会打断疾跑，疾跑中连点每下都放一圈 AOE，要求满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        // 如果是玩家，执行旋转动画
        if (attacker instanceof ServerPlayer) {
            performSpinAnimation((ServerPlayer) attacker);
        }

        // ⭐ v2.3 视觉：绕自身扫过一圈的银白刀光 + 扬尘环（纯服务端广播，不影响任何机制）
        // 半径与下方 AOE 判定共用 SWEEP_RADIUS，保证「扫到哪里就打到哪里」
        if (attacker.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.spinSlash(
                    serverLevel,
                    attacker.getX(), attacker.getY(), attacker.getZ(),
                    attacker.getYRot(), (float) SWEEP_RADIUS.get()
            );
        }

        // 计算额外伤害：原始伤害 × 10% × 等级
        float baseDamage = ctx.getDamage();
        float bonusDamage = baseDamage * effectiveLevel * (float) DAMAGE_PER_LEVEL.get();

        // 直接目标：增加额外伤害
        ctx.addDamage(bonusDamage);

        // 获取周围3格内的所有生物（排除自己、直接目标、队友）
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                attacker,
                SWEEP_RADIUS.get(),
                entity -> {
                    // 排除自己
                    if (entity.equals(attacker)) {
                        return false;
                    }
                    // 排除直接目标（已通过ctx增伤）
                    if (entity.equals(directTarget)) {
                        return false;
                    }
                    // 排除队友
                    if (entity.isAlliedTo(attacker)) {
                        return false;
                    }
                    return true;
                }
        );

        // 对周围其他敌人造成伤害
        // ⭐ v2.2：命中数量硬上限，防止密集怪物场景下无上限 hurt 触发事件链风暴
        int hitCount = 0;
        IN_SWEEP.set(Boolean.TRUE);
        try {
            for (LivingEntity target : nearbyEntities) {
                if (hitCount >= MAX_TARGETS.getInt()) {
                    break;
                }
                DamageSource damageSource = attacker.damageSources().mobAttack(attacker);
                target.hurt(damageSource, baseDamage + bonusDamage);
                hitCount++;
            }
        } finally {
            IN_SWEEP.set(Boolean.FALSE);
        }
    }

    /**
     * 执行旋转动画：玩家快速转一圈（360度）
     *
     * @param player 服务端玩家
     */
    private void performSpinAnimation(@NotNull ServerPlayer player) {
        final double initialX = player.getX();
        final double initialY = player.getY();
        final double initialZ = player.getZ();
        final float initialYaw = player.getYRot();
        final float initialPitch = player.getXRot();

        // 旋转持续时间：12 tick（0.6秒）
        final int duration = 12;
        final float degreePerTick = 360.0f / duration;

        new SynchronizationTask(0, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > duration || !player.isAlive()) {
                    player.connection.teleport(initialX, initialY, initialZ, initialYaw, initialPitch);
                    this.cancel();
                    return;
                }

                float currentYaw = initialYaw + (degreePerTick * tick);
                while (currentYaw >= 360.0f) currentYaw -= 360.0f;
                while (currentYaw < 0.0f) currentYaw += 360.0f;

                player.connection.teleport(initialX, initialY, initialZ, currentYaw, initialPitch);
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
