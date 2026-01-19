package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
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
 * @version 2.0
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

        if (EnchantmentDataManager.isOnCooldown("greatblade_phalanx", hurter.getUUID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("greatblade_phalanx", hurter.getUUID(), 6000);

        for (int i = 0; i < 3; i++) {
            EntityGlintblades entityGlintbladesShow = new EntityGlintblades(hurter, attacker)
                    .setDeadTick(75 + i * 25)
                    .setSize(7.5f);

            double posY = entityGlintbladesShow.getY() + 5;
            double posX = entityGlintbladesShow.getX();
            double posZ = entityGlintbladesShow.getZ();

            if (i == 0) {
                posX -= 10;
                posZ += 10;
            } else if (i == 1) {
                posX -= 10;
                posZ -= 10;
            } else {
                posX += 10;
            }

            entityGlintbladesShow.setPos(posX, posY, posZ);
            hurter.level().addFreshEntity(entityGlintbladesShow);

            int finalLevel = level;
            double finalPosX = posX;
            double finalPosY = posY;
            double finalPosZ = posZ;

            new SynchronizationTask(75 + i * 25) {
                @Override
                public void run() {
                    EntityGlintblades entityGlintblades = new EntityGlintblades(hurter, attacker);
                    entityGlintblades.setSize(7.5f);
                    entityGlintblades.setPos(finalPosX, finalPosY, finalPosZ);

                    // 使用原版的间接魔法伤害（投射物魔法伤害）
                    entityGlintblades.setDamageSource(
                            hurter.damageSources().indirectMagic(entityGlintblades, hurter)
                    );
                    entityGlintblades.setDamage(
                            (attacker.getMaxHealth() - attacker.getHealth()) * finalLevel * 0.1f
                    );
                    entityGlintblades.shoot(1);

                    hurter.level().addFreshEntity(entityGlintblades);
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