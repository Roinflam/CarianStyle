package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 癫火蔓延附魔
 * <p>
 * 受到致命伤害时触发：
 * - 保留30%最大生命值
 * - 击退周围敌人
 * - 1.5秒后对范围内所有实体施加癫火灼烧效果
 * - 持续3秒的伤害，最终自身死亡
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    public EnchantmentEpilepsySpread() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("epilepsy_spread_cooldown", hurter.getUUID())) {
            return;
        }

        if (hurter.getHealth() - ctx.getDamage() <= hurter.getMaxHealth() * 0.3) {
            EnchantmentDataManager.setData("epilepsy_spread_active", hurter.getUUID(), true);
            EnchantmentDataManager.setCooldown("epilepsy_spread_cooldown", hurter.getUUID(), 1800);

            ctx.cancelEvent();
            hurter.setHealth(hurter.getMaxHealth() * 0.3f);
            hurter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));

            List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    hurter,
                    level * 4
            );

            for (LivingEntity entityLivingBase : entities) {
                entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    double x = entityLivingBase.getX() - hurter.getX();
                    double z = entityLivingBase.getZ() - hurter.getZ();
                    float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockback(stronge, x, z);
                }
            }

            int finalLevel = level;
            new SynchronizationTask(30) {
                @Override
                public void run() {
                    if (!entities.isEmpty()) {
                        for (Entity entity : entities) {
                            LivingEntity entityLivingBase = (LivingEntity) entity;
                            entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);
                            entityLivingBase.addEffect(new MobEffectInstance(CarianStylePotion.EPILEPSY_FIRE_BURNING.get(), 3 * 20 + 5, 0));

                            new SynchronizationTask(5, 1) {
                                private int tick = 0;

                                @Override
                                public void run() {
                                    if (++tick > 60 || !entityLivingBase.isAlive()) {
                                        this.cancel();
                                        return;
                                    }

                                    if (entityLivingBase.equals(hurter)) {
                                        float damage = hurter.getMaxHealth() * 0.3f / 60;
                                        if (hurter.getHealth() - damage * 2 > 0) {
                                            // 使用真伤系统
                                            EntityLivingUtil.damageHealthDirectly(hurter, damage);
                                        } else {
                                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUUID());
                                            EntityLivingUtil.kill(hurter, NewDamageSource.epilepsyFire(hurter.level()));
                                            this.cancel();
                                        }
                                    } else {
                                        float damage = hurter.getMaxHealth() * finalLevel * 0.3f * 2 / 60;
                                        if (entityLivingBase.getHealth() - damage * 2 > 0) {
                                            // 使用真伤系统
                                            EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);
                                        } else {
                                            EntityLivingUtil.kill(entityLivingBase, NewDamageSource.epilepsyFire(entityLivingBase.level()));
                                            this.cancel();
                                        }
                                    }
                                }
                            }.start();
                        }
                    }

                    new SynchronizationTask(66) {
                        @Override
                        public void run() {
                            EntityLivingUtil.kill(hurter, NewDamageSource.epilepsyFire(hurter.level()));
                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUUID());
                        }
                    }.start();
                }
            }.start();
        }
    }

    @Override
    protected void onDefendHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", hurter.getUUID());

        if (isActive != null && isActive) {
            ctx.cancelEvent();
        }
    }

    @Mod.EventBusSubscriber
    public static class ClientEventHandler {

        @SubscribeEvent
        public static void onLivingUpdate(@NotNull LivingEvent.LivingTickEvent evt) {
            if (evt.getEntity().level().isClientSide) {
                LivingEntity entityLiving = evt.getEntity();
                Boolean isActive = EnchantmentDataManager.getData("epilepsy_spread_active", entityLiving.getUUID());

                if (isActive != null && isActive) {
                    EntityLivingUtil.setJumped(entityLiving);
                }
            }
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