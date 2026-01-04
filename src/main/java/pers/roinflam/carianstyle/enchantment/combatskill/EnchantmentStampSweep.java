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
 * - 冲刺攻击时，玩家快速转一圈（360度旋转）
 * - 对自身周围3格的所有敌人（包括被直接攻击的目标）造成额外伤害
 * - 伤害 = 本次攻击伤害 + 10% × 等级
 * - 不会伤害队友
 */
@AutoRegisterEnchantment(
        id = "stamp_sweep",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentStampSweep extends EnchantmentBase {

        /**
         * 构造函数
         */
        public EnchantmentStampSweep() {
                super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
        }

        /**
         * 攻击时触发：冲刺状态下对周围敌人造成范围伤害并旋转视角
         */
        @Override
        protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
                EntityLivingBase attacker = ctx.getHolder();
                EntityLivingBase directTarget = ctx.getVictim();

                // 被攻击的目标不能为空
                if (directTarget == null) {
                        return;
                }

                // 必须在冲刺状态
                if (!attacker.isSprinting()) {
                        return;
                }

                // 手动应用等级限制
                int effectiveLevel = level;
                if (ConfigLoader.levelLimit) {
                        effectiveLevel = Math.min(effectiveLevel, 10);
                }

                // 如果是玩家，执行旋转动画
                if (attacker instanceof EntityPlayerMP) {
                        performSpinAnimation((EntityPlayerMP) attacker);
                }

                // 计算额外伤害：原始伤害 × 10% × 等级
                float baseDamage = ctx.getDamage();
                float bonusDamage = baseDamage * effectiveLevel * 0.1f;

                // 获取周围3格内的所有生物
                List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        attacker,
                        3,
                        entity -> {
                                // 排除自己
                                if (entity.equals(attacker)) {
                                        return false;
                                }
                                // 排除队友（同队的不攻击）
                                if (entity.isOnSameTeam(attacker)) {
                                        return false;
                                }
                                return true;
                        }
                );

                // 对周围所有敌人造成伤害
                for (EntityLivingBase target : nearbyEntities) {
                        // 如果是被直接攻击的目标，给它增加额外伤害
                        if (target.equals(directTarget)) {
                                // 直接目标：在原有伤害基础上增加10%
                                ctx.addDamage(bonusDamage);
                        } else {
                                // 周围其他敌人：造成完整伤害（原始伤害 + 10%）
                                DamageSource damageSource = DamageSource.causeMobDamage(attacker);
                                target.attackEntityFrom(damageSource, baseDamage + bonusDamage);
                        }
                }

                // 如果周围没有其他敌人，只给直接目标增加伤害
                if (nearbyEntities.isEmpty() || !nearbyEntities.contains(directTarget)) {
                        ctx.addDamage(bonusDamage);
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