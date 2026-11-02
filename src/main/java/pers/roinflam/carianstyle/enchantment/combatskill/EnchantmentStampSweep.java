package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import java.util.List;
import javax.annotation.Nonnull;

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
 *   直接目标通过ctx增伤，周围其他敌人通过 attackEntityFrom 造成伤害
 * </p>
 *
 * <h3>性能安全上限（v2.2 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_TARGETS}：单次冲刺斩最大命中目标数上限。
 *       原逻辑对 3 格内所有敌人无上限造成伤害，且每次都使用带攻击者实体的
 *       生物近战伤害源，会触发每个目标各自的受击类附魔（如因果律累积、各类反伤）。
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
 * <p><b>视觉与判定严格对齐：</b>特效半径与下方 AOE 判定共用 {@link #SWEEP_RADIUS}——
 * 刀光扫到哪里就打到哪里，玩家可以据此走位。</p>
 * <p>只加视觉，机制不变：旋转动画、增伤公式、目标筛选、命中上限全部保持原样；
 * 特效为纯服务端广播，不生成实体、不触发任何事件。触发条件是「冲刺攻击」，
 * 频率天然受控，不会刷屏。</p>
 *
 * <h3>v2.4：AOE 重入连锁 + 满蓄力门槛</h3>
 * <p><b>问题一：AOE 会把自己再触发一遍。</b>周围目标挨的是以攻击者为来源的生物近战伤害，
 * 直接实体就是攻击者本人，于是每个 AOE 目标的 LivingDamageEvent 都会重新分发到本附魔；
 * 而原版要等这一下结算完之后才取消疾跑，嵌套那一层里攻击者仍在疾跑，条件全部满足。
 * 结果是沿着怪群深度优先地一层套一层：每层再对周围最多 {@link #MAX_TARGETS} 个目标各打一次，
 * 伤害每层再乘 {@code 1 + 10% × 等级}，旋转任务和刀光也每层各放一次，
 * 身边挤着 12 只僵尸时，一下冲刺斩实测打出 1600 多次 LivingAttackEvent（命中数量上限只管一层，管不住层数）。</p>
 * <p><b>修复：</b>AOE 循环前后置位 / 清除 {@link #IN_SWEEP}（try/finally，异常也会清掉），
 * 进入本方法时已置位就直接返回。AOE 目标照常挨这一下，只是不再从它身上再放一圈。</p>
 * <p><b>问题二：连点刷 AOE。</b>武器不带击退附魔时，原版低蓄力命中不会打断疾跑，疾跑中连点每一下都放一圈 AOE、
 * 再拉一次旋转。现在玩家必须满蓄力（{@link EnchantmentBase#isFullyCharged}）才触发，
 * 未满蓄力就当没有这个附魔；怪物持有者不受影响。</p>
 * <p>
 * 1.12.2 版：转圈靠 {@code connection.setPlayerLocation} 每 tick 发一次位置朝向。
 * 没有网络连接的玩家（模组造的假玩家，{@code connection} 为空）不转圈，范围伤害照常结算。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@AutoRegisterEnchantment(
        id = "stamp_sweep",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentStampSweep extends EnchantmentBase {

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
     * 正在结算 AOE 时置位（v2.4）。
     * <p>AOE 循环里的 {@code target.hurt} 会同步触发新的 LivingDamageEvent 并回到本附魔，
     * 置位期间直接放行，保证一次冲刺斩只放一圈。只在服务端线程读写，用 ThreadLocal 是为了不和别的线程互相干扰。</p>
     */
    private static final ThreadLocal<Boolean> IN_SWEEP = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentStampSweep() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发：冲刺状态下对周围敌人造成范围伤害并旋转视角
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        // v2.4：AOE 自己打出的伤害会再次进入这里，正在结算 AOE 时直接放行（详见类注释）
        if (IN_SWEEP.get()) {
            return;
        }

        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase directTarget = ctx.getVictim();

        if (directTarget == null) {
            return;
        }

        // 必须在冲刺状态
        if (!attacker.isSprinting()) {
            return;
        }

        // v2.4：武器不带击退附魔时，原版低蓄力命中不会打断疾跑，疾跑中连点每下都放一圈 AOE，要求满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        effectiveLevel = capLevel(effectiveLevel, LEVEL_CAP.getInt());

        // 如果是玩家，执行旋转动画
        if (attacker instanceof EntityPlayerMP) {
            performSpinAnimation((EntityPlayerMP) attacker);
        }

        // v2.3 视觉：绕自身扫过一圈的银白刀光 + 扬尘环（纯服务端广播，不影响任何机制）
        // 半径与下方 AOE 判定共用 SWEEP_RADIUS，保证「扫到哪里就打到哪里」
        if (attacker.world instanceof WorldServer) {
            CarianStyleCombatArtEffects.spinSlash(
                    (WorldServer) attacker.world,
                    attacker.posX, attacker.posY, attacker.posZ,
                    attacker.rotationYaw, (float) SWEEP_RADIUS.get()
            );
        }

        // 计算额外伤害：原始伤害 × 10% × 等级
        float baseDamage = ctx.getDamage();
        float bonusDamage = baseDamage * effectiveLevel * (float) DAMAGE_PER_LEVEL.get();

        // 直接目标：增加额外伤害
        ctx.addDamage(bonusDamage);

        // 获取周围3格内的所有生物（排除自己、直接目标、队友）
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
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
                    if (entity.isOnSameTeam(attacker)) {
                        return false;
                    }
                    return true;
                }
        );

        // 对周围其他敌人造成伤害
        // v2.2：命中数量硬上限，防止密集怪物场景下无上限 hurt 触发事件链风暴
        int hitCount = 0;
        IN_SWEEP.set(Boolean.TRUE);
        try {
            for (EntityLivingBase target : nearbyEntities) {
                if (hitCount >= MAX_TARGETS.getInt()) {
                    break;
                }
                DamageSource damageSource = DamageSource.causeMobDamage(attacker);
                target.attackEntityFrom(damageSource, baseDamage + bonusDamage);
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
    private void performSpinAnimation(@Nonnull EntityPlayerMP player) {
        // 1.12.2 版：没有网络连接的假玩家发不了位置包，不转（见类注释）
        if (player.connection == null) {
            return;
        }

        final double initialX = player.posX;
        final double initialY = player.posY;
        final double initialZ = player.posZ;
        final float initialYaw = player.rotationYaw;
        final float initialPitch = player.rotationPitch;

        // 旋转持续时间：12 tick（0.6秒）
        final int duration = 12;
        final float degreePerTick = 360.0f / duration;

        new SynchronizationTask(0, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > duration || !player.isEntityAlive() || player.connection == null) {
                    if (player.connection != null) {
                        player.connection.setPlayerLocation(initialX, initialY, initialZ, initialYaw, initialPitch);
                    }
                    this.cancel();
                    return;
                }

                float currentYaw = initialYaw + (degreePerTick * tick);
                while (currentYaw >= 360.0f) currentYaw -= 360.0f;
                while (currentYaw < 0.0f) currentYaw += 360.0f;

                player.connection.setPlayerLocation(initialX, initialY, initialZ, currentYaw, initialPitch);
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
