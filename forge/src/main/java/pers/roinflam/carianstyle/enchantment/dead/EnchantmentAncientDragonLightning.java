package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.level.Level;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 古龙雷电附魔
 * <p>
 * 死亡时触发：
 * - 对60格范围内的敌人降下闪电
 * - 根据天气加成伤害倍率
 * - 持续攻击直到敌人死亡
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "ancient_dragon_lightning",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
public class EnchantmentAncientDragonLightning extends EnchantmentBase {

    public EnchantmentAncientDragonLightning() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("ancient_dragon_lightning", hurter.getUUID())) {
            return;
        }

        EnchantmentDataManager.setCooldown("ancient_dragon_lightning", hurter.getUUID(), 1800);

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                hurter,
                60,
                15,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        List<Integer> list = RandomUtil.randomList(level * 100, entities.size());

        for (int i = 0; i < entities.size(); i++) {
            LivingEntity entityLivingBase = entities.get(i);
            int timeLightning = Math.min(list.get(i), level * 15);

            new SynchronizationTask(40, 5) {
                private int time = 0;

                @Override
                public void run() {
                    if (++time > timeLightning) {
                        this.cancel();
                        return;
                    }

                    Level world = entityLivingBase.level();
                    if (world instanceof ServerLevel serverLevel) {
                        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
                        if (lightning != null) {
                            lightning.moveTo(entityLivingBase.getX(), entityLivingBase.getY(), entityLivingBase.getZ());
                            lightning.setVisualOnly(true);
                            serverLevel.addFreshEntity(lightning);
                        }
                    }

                    if (!entityLivingBase.isAlive()) {
                        this.cancel();
                        return;
                    }

                    entityLivingBase.invulnerableTime = 10;

                    int magnification = 1;
                    if (entityLivingBase.level().isRaining()) {
                        magnification *= 2;
                    }
                    if (entityLivingBase.level().isThundering()) {
                        magnification *= 4;
                    }

                    entityLivingBase.hurt(
                            entityLivingBase.damageSources().lightningBolt(),
                            entityLivingBase.getHealth() * 0.05f + entityLivingBase.getMaxHealth() * 0.005f * magnification
                    );

                    if (entityLivingBase.onGround()) {
                        double x = RandomUtils.nextBoolean() ?
                                hurter.getX() - entityLivingBase.getX() :
                                entityLivingBase.getX() - hurter.getX();
                        double z = RandomUtils.nextBoolean() ?
                                hurter.getZ() - entityLivingBase.getZ() :
                                entityLivingBase.getZ() - hurter.getZ();
                        entityLivingBase.knockback(0.2f, x, z);
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((36 + (enchantmentLevel - 1) * 20) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}