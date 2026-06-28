package pers.roinflam.carianstyle.enchantment.dead;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.visual.effect.CarianStyleBurstParticles;

import java.util.ArrayList;
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
 * <p>
 * 修复记录 v2.1：
 * - 击退方向修正：原代码传入 entity-hurter（从hurter指向entity），
 *   knockback内部取反后把敌人拉向hurter。应传入 hurter-entity 才能推开。
 * </p>
 *
 * <h3>性能安全上限（v2.2 新增）</h3>
 * <ul>
 *   <li>{@link #MAX_SEARCH_RADIUS}：AOE 搜索半径硬上限，防止 level*4 在 100 级时达 400 格。</li>
 *   <li>{@link #MAX_TARGETS}：单次触发最大命中目标数上限，防止对大量实体创建
 *       并发 SynchronizationTask 导致 tick 调度器过载。</li>
 * </ul>
 *
 * <p>本附魔为每个命中目标创建一个独立的 SynchronizationTask（60tick 循环任务），
 * 100 级 + 400 格范围 + 200 个实体 = 200 个并发 tick 任务，
 * 即使有 1800tick 冷却，单次触发就是灾难级性能打击。</p>
 *
 * <p>v2.3：延迟爆发阶段新增癫火（橙色火焰）团状爆发粒子视觉（单数据包 sendParticles 广播，
 * 不新增网络包，不触碰任何并发任务 / 上限 / 清理逻辑）。</p>
 *
 * <p>v2.4：特效起播时机修复 —— 将 {@link CarianStyleBurstParticles#burst} 调用从延迟 30tick 的
 * 爆发任务前移至 {@link #onDamageAsVictimLowest} 触发入口，并把中心 Y 由身体中部
 * （{@code getY()+bbHeight*0.5}）改为脚底地面（{@code getY()}）。配合客户端 FRENZIED_FLAME 平面演出
 * （爆发主点 p≈0.42 对齐 30tick），实现「受致命伤那一刻癫火狂乱裂纹即从脚下铺开 → 覆盖 1.5s 蓄能 →
 * 1.5s 后爆发与灼烧伤害阶段同步 → 焦黑余烬」。原延迟任务中的特效调用已移除，
 * 并发灼烧任务 / 上限 / 击退 / 清理逻辑完全不变。</p>
 *
 * <p>v2.5：特效改为<b>跟随实体</b> —— 调用带 {@link Entity} 的
 * {@code burst} 重载并传入 hurter，特效包携带其实体 id，客户端每帧取实体实时插值位置作为癫火中心
 * （触发后被击退 / 视角移动时狂乱裂纹贴身不脱离）；实体死亡 / 移除后客户端回退到最后已知坐标继续
 * 播放余烬。机制逻辑（并发灼烧任务 / 上限 / 击退 / 自死）仍完全不变。</p>
 *
 * @author RoinFlam
 * @version 2.5
 */
@AutoRegisterEnchantment(
        id = "epilepsy_spread",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.DEAD,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
public class EnchantmentEpilepsySpread extends EnchantmentBase {

    /** AOE 搜索半径硬上限（方块）：不管等级多高，最多搜索半径 16 方块 */
    private static final int MAX_SEARCH_RADIUS = 16;

    /** 单次触发最大命中目标数：防止并发 SynchronizationTask 过载 */
    private static final int MAX_TARGETS = 24;

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

            // ⭐ v2.4：特效起播时机修复 —— 改至触发瞬间在脚底地面生成癫火演出
            // （FLAME 经 CarianStyleBurstParticles.burst → frenziedFlame 触发客户端 FRENZIED_FLAME
            // 平面演出）。中心 Y 用 getY()（脚底），狂乱裂纹/冲击贴地铺开；客户端爆发主点 p≈0.42
            // 正好对齐下方延迟 30tick 的灼烧爆发阶段。
            // ⭐ v2.5：改调带 entity 的 burst 重载、把 hurter 传入 —— 特效绑定该实体 id，客户端每帧
            // 跟随实体实时位置（被击退 / 拉拽 / 视角移动时癫火裂纹贴身不脱离）；实体死亡后由客户端回退
            // 到最后位置继续播余烬。纯服务端广播，粒子不触发任何事件，不影响下方并发灼烧任务 / 上限 / 清理逻辑
            if (hurter.level() instanceof ServerLevel serverLevel) {
                CarianStyleBurstParticles.burst(
                        serverLevel, hurter,
                        hurter.getX(), hurter.getY(), hurter.getZ(),
                        40, ParticleTypes.FLAME, 1.2, 0.1
                );
            }

            // ⭐ v2.2：搜索半径硬上限，防止等级×4直接当半径
            // 原：level * 4（100级 = 400格）
            int searchRadius = Math.min(level * 4, MAX_SEARCH_RADIUS);

            List<LivingEntity> rawEntities = EntityUtil.getNearbyEntities(
                    LivingEntity.class,
                    hurter,
                    searchRadius
            );

            // ⭐ v2.2：命中数量硬上限，防止并发 SynchronizationTask 过载
            // 使用单独的列表存储被处理的实体，供延迟任务复用
            List<LivingEntity> entities = new ArrayList<>();
            int hitCount = 0;
            for (LivingEntity entityLivingBase : rawEntities) {
                if (hitCount >= MAX_TARGETS) {
                    break;
                }
                entities.add(entityLivingBase);

                entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);
                if (!entityLivingBase.equals(hurter)) {
                    // v2.1修复：方向改为 hurter - entity（从entity指向hurter），
                    // knockback内部取反后把entity推离hurter
                    double x = hurter.getX() - entityLivingBase.getX();
                    double z = hurter.getZ() - entityLivingBase.getZ();
                    float stronge = (float) (level * 0.7 * Math.max(Math.abs(x), Math.abs(z)) / 14);
                    entityLivingBase.knockback(stronge, x, z);
                }
                hitCount++;
            }

            int finalLevel = level;
            new SynchronizationTask(30) {
                @Override
                public void run() {
                    // v2.4：延迟爆发阶段的特效调用已前移至 onDamageAsVictimLowest 触发入口（见上方），
                    // 此处仅保留并发灼烧伤害 / 自身死亡逻辑，时序与数值完全不变

                    if (!entities.isEmpty()) {
                        for (Entity entity : entities) {
                            LivingEntity entityLivingBase = (LivingEntity) entity;
                            entityLivingBase.playSound(SoundEvents.GHAST_HURT, 1, 1);

                            // 应用火焰燃烧效果（需要同步网络）
                            DynamicAttributeManager.apply(entityLivingBase,
                                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(3 * 20 + 5, 0));
                            ClientSyncEffectHelper.onAttributeApplied(entityLivingBase, DynamicAttributes.EPILEPSY_FIRE_BURNING);

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
                                            EntityLivingUtil.damageHealthDirectly(hurter, damage);
                                        } else {
                                            EnchantmentDataManager.removeData("epilepsy_spread_active", hurter.getUUID());
                                            EntityLivingUtil.kill(hurter, NewDamageSource.epilepsyFire(hurter.level()));
                                            this.cancel();
                                        }
                                    } else {
                                        float damage = hurter.getMaxHealth() * finalLevel * 0.3f * 2 / 60;
                                        if (entityLivingBase.getHealth() - damage * 2 > 0) {
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
