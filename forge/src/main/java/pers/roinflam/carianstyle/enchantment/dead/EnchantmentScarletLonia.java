package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
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
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

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
 * <p>v2.3：第二阶段爆发时新增猩红孢子团状爆发粒子视觉（单数据包 sendParticles 广播，
 * 不新增网络包，不触碰任何双阶段 AOE / 上限 / 清理逻辑）。</p>
 *
 * <p>v2.4：特效起播时机修复 —— 将 {@link CarianStyleBurstParticles#burst} 调用从延迟 30tick 的
 * 第二阶段爆发任务前移至 {@link #onDeath} 触发入口，并把中心 Y 由身体中部（{@code getY()+bbHeight*0.5}）
 * 改为脚底地面（{@code getY()}）。配合客户端 SCARLET_BLOOM 立体花演出（盛放主点 p≈0.42 对齐 30tick），
 * 实现「受致命伤那一刻立体花即从地面绽放 → 缓慢张开覆盖 1.5s 拉取无敌蓄能 → 1.5s 后盛放爆发与第二阶段
 * 伤害同步 → 凋谢余波」。原延迟任务中的特效调用已移除，双阶段 AOE / 上限 / 击退 / 清理逻辑完全不变。</p>
 *
 * <p>v2.5：特效改为<b>跟随实体</b> —— 调用带 {@link Entity} 的
 * {@code burst} 重载并传入 hurter，特效包携带其实体 id，客户端每帧取实体实时插值位置作为立体花中心
 * （受致命伤后被击退 / 视角移动时花贴身不脱离）；实体死亡 / 移除后客户端回退到最后已知坐标继续播放
 * 凋谢余波。机制逻辑（双阶段 AOE / 上限 / 击退 / 自死）仍完全不变。</p>
 *
 * @author RoinFlam
 * @version 2.5
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

        // ⭐ v2.4：特效起播时机修复 —— 改至触发瞬间在脚底地面生成立体花
        // （CRIMSON_SPORE 经 CarianStyleBurstParticles.burst → scarletBloom 触发客户端 SCARLET_BLOOM
        // 立体花演出）。中心 Y 用 getY()（脚底），立体花从地面长起；客户端盛放主点 p≈0.42 正好对齐
        // 下方延迟 30tick 的第二阶段爆发。
        // ⭐ v2.5：改调带 entity 的 burst 重载、把 hurter 传入 —— 特效绑定该实体 id，客户端每帧跟随
        // 实体实时位置（被击退 / 拉拽 / 视角移动时立体花贴身不脱离）；实体死亡后由客户端回退到最后
        // 位置继续播凋谢。纯服务端广播，粒子不触发任何事件，不影响下方双阶段 AOE / 上限 / 击退 / 清理逻辑
        if (hurter.level() instanceof ServerLevel serverLevel) {
            CarianStyleBurstParticles.burst(
                    serverLevel, hurter,
                    hurter.getX(), hurter.getY(), hurter.getZ(),
                    40, ParticleTypes.CRIMSON_SPORE, 1.2, 0.1
            );
        }

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
                // v2.4：第二阶段爆发的特效调用已前移至 onDeath 触发入口（见上方），
                // 此处仅保留双阶段 AOE 伤害 / 击退 / 自身死亡逻辑，时序与数值完全不变

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
