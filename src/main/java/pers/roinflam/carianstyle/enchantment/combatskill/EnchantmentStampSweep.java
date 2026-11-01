// 文件：EnchantmentStampSweep.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentStampSweep.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 箭步回旋斩附魔
 *
 * 效果：
 * - 满蓄力冲刺攻击时，玩家快速转一圈（360度旋转）
 * - 直接目标伤害 +10% × 等级；自身周围3格（水平圆）内其它敌人受到 本次伤害 + 10% × 等级，最多 20 个
 * - 不会伤害自己和队友
 * <p>
 * 以前没有蓄力要求，冲刺连点每下都转一圈；范围目标不设上限；
 * 范围伤害打到别人时又会回到本附魔再扫一圈，形成连锁。现在要求满蓄力、最多打 20 个，
 * 结算范围伤害期间不再重入。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "stamp_sweep",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentStampSweep extends EnchantmentBase {

        /** 单次横扫最多命中的其它目标数 */
        private static final int MAX_TARGETS = 20;
        /** 横扫半径（格） */
        private static final double SWEEP_RADIUS = 3.0D;
        /** 参与计算的等级上限 */
        private static final int LEVEL_CAP = 10;
        /** 每级的额外伤害倍率 */
        private static final float DAMAGE_PER_LEVEL = 0.1F;

        /**
         * 正在结算范围伤害时置位：循环里的 attackEntityFrom 会同步触发新的伤害事件回到本附魔，
         * 置位期间直接放行，一次冲刺斩只放一圈
         */
        private static boolean inSweep = false;

        /**
         * 构造函数
         */
        public EnchantmentStampSweep() {
                super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
        }

        /**
         * 攻击时触发：满蓄力冲刺攻击时对周围敌人造成范围伤害并旋转视角
         */
        @Override
        protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
                if (inSweep) {
                        return;
                }

                EntityLivingBase attacker = ctx.getHolder();
                EntityLivingBase directTarget = ctx.getVictim();

                if (directTarget == null) {
                        return;
                }

                if (!attacker.isSprinting()) {
                        return;
                }

                // 不带击退的武器低蓄力命中不会打断疾跑，连点每下都放一圈，所以要求满蓄力
                if (!isFullyCharged(attacker)) {
                        return;
                }

                int effectiveLevel = capLevel(level, LEVEL_CAP);

                if (attacker instanceof EntityPlayerMP) {
                        performSpinAnimation((EntityPlayerMP) attacker);
                }

                // 额外伤害：原始伤害 × 10% × 等级
                float baseDamage = ctx.getDamage();
                float bonusDamage = baseDamage * effectiveLevel * DAMAGE_PER_LEVEL;

                // 直接目标单独加伤，不在范围里也照加
                ctx.addDamage(bonusDamage);

                // 周围3格内的其它生物（排除自己、直接目标、队友）
                List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        attacker,
                        SWEEP_RADIUS,
                        entity -> entity != null
                                && !entity.equals(attacker)
                                && !entity.equals(directTarget)
                                && !entity.isOnSameTeam(attacker)
                );

                int hitCount = 0;
                inSweep = true;
                try {
                        for (EntityLivingBase target : nearbyEntities) {
                                if (hitCount >= MAX_TARGETS) {
                                        break;
                                }
                                DamageSource damageSource = DamageSource.causeMobDamage(attacker);
                                target.attackEntityFrom(damageSource, baseDamage + bonusDamage);
                                hitCount++;
                        }
                } finally {
                        inSweep = false;
                }
        }

        /**
         * 执行旋转动画：玩家快速转一圈（360度）
         *
         * @param player 服务端玩家
         */
        private void performSpinAnimation(@Nonnull EntityPlayerMP player) {
                // 记录初始位置和朝向
                final double initialX = player.posX;
                final double initialY = player.posY;
                final double initialZ = player.posZ;
                final float initialYaw = player.rotationYaw;
                final float initialPitch = player.rotationPitch;

                // 旋转持续时间：8 tick（0.4秒）
                final int duration = 12;

                // 每tick旋转角度：360度 / 8tick = 45度/tick
                final float degreePerTick = 360.0f / duration;

                // 创建旋转任务
                new SynchronizationTask(0, 1) {
                        private int tick = 0;

                        @Override
                        public void run() {
                                if (++tick > duration || !player.isEntityAlive()) {
                                        // 旋转结束，恢复到初始朝向
                                        player.connection.setPlayerLocation(initialX, initialY, initialZ, initialYaw, initialPitch);
                                        this.cancel();
                                        return;
                                }

                                // 计算当前旋转角度
                                float currentYaw = initialYaw + (degreePerTick * tick);

                                // 确保角度在0-360范围内
                                while (currentYaw >= 360.0f) {
                                        currentYaw -= 360.0f;
                                }
                                while (currentYaw < 0.0f) {
                                        currentYaw += 360.0f;
                                }

                                // 使用 setPlayerLocation 同步位置和朝向到客户端
                                player.connection.setPlayerLocation(initialX, initialY, initialZ, currentYaw, initialPitch);
                        }
                }.start();
        }

        @Override
        public int getMinEnchantability(int enchantmentLevel) {
                // RARE 的默认公式：10 + (level - 1) * 15
                return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
        }
}