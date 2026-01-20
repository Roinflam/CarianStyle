package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 巨剑方阵附魔
 * <p>
 * 死亡时触发：
 * - 在空中生成3把巨大的辉石剑
 * - 延迟后向攻击者发射
 * - 伤害基于攻击者损失的生命值
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "greatblade_phalanx",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentGreatbladePhalanx extends EnchantmentBase {

    public EnchantmentGreatbladePhalanx() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        if (!(ctx.getDamageSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();
        LivingEntity attacker = (LivingEntity) ctx.getDamageSource().getEntity();

        // 检查冷却
        if (EnchantmentDataManager.isOnCooldown("greatblade_phalanx", hurter.getUUID())) {
            return;
        }

        // 设置冷却（6000tick = 5分钟）
        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUUID(), 6000);

        // 生成3把巨剑
        for (int i = 0; i < 3; i++) {
            // 计算悬浮位置（在死亡位置上方形成三角阵型）
            double posY = hurter.getY() + 5;
            double posX = hurter.getX();
            double posZ = hurter.getZ();

            if (i == 0) {
                posX -= 10;
                posZ += 10;
            } else if (i == 1) {
                posX -= 10;
                posZ -= 10;
            } else {
                posX += 10;
            }

            // 延迟时间递增（形成连击效果）
            int delayTicks = 75 + i * 25;

            // 显示用的剑（悬浮效果）
            EntityGlintblades showBlade = new EntityGlintblades(hurter, attacker)
                    .setDeadTick(delayTicks)
                    .setSize(7.5f);
            showBlade.setPos(posX, posY, posZ);
            hurter.level().addFreshEntity(showBlade);

            // 保存位置到final变量供延迟任务使用
            int finalLevel = level;
            double finalPosX = posX;
            double finalPosY = posY;
            double finalPosZ = posZ;

            // 延迟发射攻击剑
            new SynchronizationTask(delayTicks) {
                @Override
                public void run() {
                    // 再次检查目标是否存在
                    if (!attacker.isAlive() || attacker.isRemoved()) {
                        return;
                    }

                    // 创建攻击剑
                    EntityGlintblades attackBlade = new EntityGlintblades(hurter, attacker)
                            .setSize(7.5f)
                            .setDamage((attacker.getMaxHealth() - attacker.getHealth()) * finalLevel * 0.1f)
                            .setDamageSource(hurter.damageSources().indirectMagic(null, hurter))
                            .setTrackingStrength(0.08f)  // 巨剑追踪较慢（更有重量感）
                            .setMaxLifetime(120);         // 6秒存活时间

                    attackBlade.setPos(finalPosX, finalPosY, finalPosZ);
                    attackBlade.shoot(1.0f);  // 降低初始速度，依靠追踪
                    hurter.level().addFreshEntity(attackBlade);
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}