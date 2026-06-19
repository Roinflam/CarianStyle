package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 猩红罗妮亚附魔
 * <p>
 * 死亡时触发：
 * - 取消死亡，保留1点生命
 * - 击退周围敌人
 * - 1.5秒后对范围内敌人造成猩红腐败伤害并击退
 * - 最终自身死亡
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - 第一次击退方向修正：原代码传入 entity-hurter（从hurter指向entity），
 *   knockback内部取反后把敌人拉向hurter。应传入 hurter-entity 才能推开。
 * </p>
 *
 * <h3>性能安全上限（v2.2 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_KNOCKBACK_RADIUS}：第一次击退搜索半径硬上限，防止 level*4 在 100 级时达 400 格。</li>
 *   <li>{@link #MAX_DAMAGE_RADIUS}：第二次伤害搜索半径硬上限，防止 finalLevel*2 在 100 级时达 200 格。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止密集怪物场景下事件风暴。</li>
 * </ul>
 *
 * <p>本附魔虽有 1800tick 冷却，但一次触发就是双阶段 AOE 爆炸，
 * 第二阶段还对每个目标调用 damageHealthDirectly + addEffect + knockback 三重事件链，
 * 高等级下单次触发就可导致 tick 耗时突破看门狗阈值。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "scarlet_lonia",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentScarletLonia extends EnchantmentBase {

    /** 第一次击退阶段 AOE 搜索半径硬上限（方块） */
    private static final int MAX_KNOCKBACK_RADIUS = 16;

    /** 第二次伤害阶段 AOE 搜索半径硬上限（方块） */
    private static final int MAX_DAMAGE_RADIUS = 12;

    /** 单次触发最大命中目标数：防止密集怪物场景下事件风暴 */
    private static final int MAX_TARGETS = 24;

    public EnchantmentScarletLonia() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    protected void onDeath(@NotNull EnchantmentContext ctx, int level) {
        if (ctx.canHarmInCreative()) {
            return;
        }

        LivingEntity hurter = ctx.getHolder();

        if (EnchantmentDataManager.isOnCooldown("scarlet_lonia_cooldown", hurter.getUUID())) {
            Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUUID());
            if (isActive != null && isActive) {
                ctx.cancelEvent();
                hurter.setHealth(1);
            }
            return;
        }

        EnchantmentDataManager.setData("scarlet_lonia_active", hurter.getUUID(), true);
        EnchantmentDataManager.setCooldown("scarlet_lonia_cooldown", hurter.getUUID(), 1800);

        ctx.cancelEvent();
        hurter.setHealth(1);
        hurter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 6));

        // ⭐ v2.2：第一次击退搜索半径硬上限
        // 原：level * 4（100级 = 400格）
        int knockbackRadius = Math.min(level * 4, MAX_KNOCKBACK_RADIUS);

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                hurter,
                knockbackRadius,
                entityLivingBase -> !entityLivingBase.equals(hurter)
        );

        // ⭐ v2.2：第一次击退命中数量硬上限
        int knockbackHitCount = 0;
        for (LivingEntity entityLivingBase : entities) {
            if (knockbackHitCount >= MAX_TARGETS) {
                break;
            }
            // v2.1修复：方向改为 hurter - entity（从entity指向hurter），
            // knockback内部取反后把entity推离hurter
            double x = hurter.getX() - entityLivingBase.getX();
            double z = hurter.getZ() - entityLivingBase.getZ();
            float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
            entityLivingBase.knockback(stronge, x, z);
            knockbackHitCount++;
        }

        int finalLevel = level;
        new SynchronizationTask(30) {
            @Override
            public void run() {
                // ⭐ v2.2：第二次伤害搜索半径硬上限
                // 原：finalLevel * 2（100级 = 200格）
                int damageRadius = Math.min(finalLevel * 2, MAX_DAMAGE_RADIUS);

                List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        hurter,
                        damageRadius,
                        entityLivingBase -> !entityLivingBase.equals(hurter)
                );

                if (!nearbyEntities.isEmpty()) {
                    // ⭐ v2.2：第二次伤害命中数量硬上限
                    int damageHitCount = 0;
                    for (Entity entity : nearbyEntities) {
                        if (damageHitCount >= MAX_TARGETS) {
                            break;
                        }
                        LivingEntity entityLivingBase = (LivingEntity) entity;
                        // 使用真伤系统
                        float damage = entityLivingBase.getHealth() * finalLevel * 0.05f;
                        EntityLivingUtil.damageHealthDirectly(entityLivingBase, damage);

                        entityLivingBase.addEffect(new MobEffectInstance(CarianStylePotion.SCARLET_ROT.get(), finalLevel * 10 * 20, finalLevel - 1));

                        // 第二次击退方向原本就正确（hurter - entity）
                        double x = hurter.getX() - entityLivingBase.getX();
                        double z = hurter.getZ() - entityLivingBase.getZ();
                        entityLivingBase.knockback(finalLevel * 0.75f, x, z);
                        damageHitCount++;
                    }
                }

                EnchantmentDataManager.removeData("scarlet_lonia_active", hurter.getUUID());
                EntityLivingUtil.kill(hurter, NewDamageSource.scarletRot(hurter.level()));
            }
        }.start();
    }

    @Override
    protected void onDefendHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity hurter = ctx.getHolder();
        Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", hurter.getUUID());

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
                Boolean isActive = EnchantmentDataManager.getData("scarlet_lonia_active", entityLiving.getUUID());

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
