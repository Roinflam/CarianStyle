package pers.roinflam.carianstyle.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pers.roinflam.carianstyle.init.CarianStyleEntity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 魔法剑实体
 * <p>
 * 用于多种魔法剑类附魔的投射物实体
 * 特性：
 * - 可追踪目标
 * - 可设置延迟发射（悬浮效果）
 * - 可自定义大小和伤害
 * - 大型剑会产生爆炸
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
public class EntityGlintblades extends ThrowableProjectile {

    // ==================== 数据参数 ====================

    /**
     * 是否已发射
     */
    private static final EntityDataAccessor<Boolean> SHOOTED =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.BOOLEAN);

    /**
     * 死亡时间（未发射状态下的存活时间）
     */
    private static final EntityDataAccessor<Integer> DEAD_TICK =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.INT);

    /**
     * 剑的大小
     */
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.FLOAT);

    // ==================== 字段 ====================

    /**
     * 伤害值
     */
    private float damage = 0;

    /**
     * 追踪目标
     */
    @Nullable
    private Entity target;

    /**
     * 伤害来源
     */
    private DamageSource damageSource;

    /**
     * 追踪强度（0-1，越大转向越快）
     */
    private float trackingStrength = 0.1f;

    /**
     * 最大存活时间（防止永久存在）
     */
    private int maxLifetime = 200;

    /**
     * 是否已命中过目标（防止重复伤害）
     */
    private boolean hasHit = false;

    // ==================== 构造函数 ====================

    /**
     * 用于注册的构造函数
     *
     * @param type  实体类型
     * @param level 世界
     */
    public EntityGlintblades(@Nonnull EntityType<? extends EntityGlintblades> type, @Nonnull Level level) {
        super(type, level);
        this.target = null;
        this.setNoGravity(true);
        this.damageSource = level.damageSources().magic();
    }

    /**
     * 创建实例的便捷构造函数
     *
     * @param level 世界
     */
    public EntityGlintblades(@Nonnull Level level) {
        this(CarianStyleEntity.GLINTBLADES.get(), level);
    }

    /**
     * 从投掷者创建的构造函数
     *
     * @param thrower 投掷者
     * @param target  追踪目标
     */
    public EntityGlintblades(@Nonnull LivingEntity thrower, @Nullable Entity target) {
        super(CarianStyleEntity.GLINTBLADES.get(), thrower, thrower.level());
        this.target = target;
        this.setNoGravity(true);
        this.damageSource = thrower.level().damageSources().magic();
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SHOOTED, false);
        this.entityData.define(DEAD_TICK, 0);
        this.entityData.define(SIZE, 1f);
    }

    // ==================== Getter/Setter ====================

    /**
     * 获取伤害值
     *
     * @return 伤害值
     */
    public float getDamage() {
        return damage;
    }

    /**
     * 设置伤害值
     *
     * @param damage 伤害值
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    /**
     * 获取伤害来源
     *
     * @return 伤害来源
     */
    public DamageSource getDamageSource() {
        return damageSource;
    }

    /**
     * 设置伤害来源
     *
     * @param damageSource 伤害来源
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setDamageSource(DamageSource damageSource) {
        this.damageSource = damageSource;
        return this;
    }

    /**
     * 获取追踪目标
     *
     * @return 追踪目标
     */
    @Nullable
    public Entity getTarget() {
        return target;
    }

    /**
     * 设置追踪目标
     *
     * @param target 追踪目标
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setTarget(@Nullable Entity target) {
        this.target = target;
        return this;
    }

    /**
     * 是否已发射
     *
     * @return 已发射返回true
     */
    public boolean isShooted() {
        return this.entityData.get(SHOOTED);
    }

    /**
     * 设置是否已发射
     *
     * @param shooted 是否已发射
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    private EntityGlintblades setShooted(boolean shooted) {
        this.entityData.set(SHOOTED, shooted);
        return this;
    }

    /**
     * 获取死亡时间
     *
     * @return 死亡时间（tick）
     */
    public int getDeadTick() {
        return this.entityData.get(DEAD_TICK);
    }

    /**
     * 设置死亡时间
     *
     * @param tick 死亡时间（tick）
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setDeadTick(int tick) {
        this.entityData.set(DEAD_TICK, tick);
        return this;
    }

    /**
     * 获取剑的大小
     *
     * @return 剑的大小
     */
    public float getSize() {
        return this.entityData.get(SIZE);
    }

    /**
     * 设置剑的大小
     *
     * @param size 剑的大小
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setSize(float size) {
        this.entityData.set(SIZE, size);
        return this;
    }

    /**
     * 设置追踪强度
     *
     * @param strength 追踪强度（0-1）
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setTrackingStrength(float strength) {
        this.trackingStrength = Math.max(0, Math.min(1, strength));
        return this;
    }

    /**
     * 设置最大存活时间
     *
     * @param ticks 最大存活时间（tick）
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setMaxLifetime(int ticks) {
        this.maxLifetime = ticks;
        return this;
    }

    // ==================== 核心逻辑 ====================

    /**
     * 发射魔法剑朝向目标
     *
     * @param velocity 速度倍率
     */
    public void shoot(float velocity) {
        if (target == null || !target.isAlive()) {
            this.discard();
            return;
        }

        setShooted(true);

        // 计算指向目标的方向向量（瞄准眼睛高度）
        Vec3 direction = calculateDirectionToTarget();

        if (direction != null) {
            // 归一化方向向量并应用速度
            Vec3 motion = direction.normalize().scale(velocity);
            this.setDeltaMovement(motion);

            // 设置实体朝向
            this.setYRot((float) (Math.atan2(motion.x, motion.z) * (180D / Math.PI)));
            this.setXRot((float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180D / Math.PI)));
        }
    }

    /**
     * 计算指向目标的方向向量（带预判）
     *
     * @return 方向向量，如果目标无效则返回null
     */
    @Nullable
    private Vec3 calculateDirectionToTarget() {
        if (target == null || !target.isAlive()) {
            return null;
        }

        // 目标位置（瞄准眼睛高度的80%）
        Vec3 targetPos = new Vec3(
                target.getX(),
                target.getY() + target.getEyeHeight() * 0.8,
                target.getZ()
        );

        // 简单的运动预判：根据目标当前速度预测未来位置
        Vec3 targetMotion = target.getDeltaMovement();
        double distance = this.position().distanceTo(targetPos);

        // 预判时间基于距离（距离越远预判越多，最多5tick）
        double predictionTime = Math.min(distance * 0.1, 5.0);
        Vec3 predictedPos = targetPos.add(targetMotion.scale(predictionTime));

        // 计算从当前位置到预测位置的方向
        return predictedPos.subtract(this.position());
    }

    @Override
    protected void onHit(@Nonnull HitResult result) {
        super.onHit(result);

        // 防止重复触发
        if (hasHit) {
            return;
        }
        hasHit = true;

        // 子类可重写的额外逻辑
        extraAction(result);

        // 大型剑产生爆炸
        if (getSize() > 5) {
            this.level().explode(
                    this.getOwner(),
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    getSize(),
                    Level.ExplosionInteraction.NONE
            );
        }

        // 确保销毁
        this.discard();
    }

    @Override
    protected void onHitEntity(@Nonnull EntityHitResult result) {
        super.onHitEntity(result);

        // 防止重复伤害
        if (hasHit) {
            return;
        }

        Entity hitEntity = result.getEntity();

        // 不伤害投掷者
        if (hitEntity.equals(this.getOwner())) {
            return;
        }

        // 命中生物造成伤害
        if (hitEntity instanceof LivingEntity livingTarget) {
            // 重置无敌帧并造成伤害
            int oldInvulnerableTime = livingTarget.invulnerableTime;
            livingTarget.invulnerableTime = 0;

            boolean damaged = livingTarget.hurt(damageSource, damage);

            // 恢复部分无敌帧（防止其他伤害源立即叠加）
            if (damaged) {
                livingTarget.invulnerableTime = Math.min(oldInvulnerableTime, livingTarget.invulnerableDuration / 2);
            }
        }
    }

    /**
     * 命中时的额外逻辑（子类可重写）
     *
     * @param result 命中结果
     */
    public void extraAction(HitResult result) {
        // 默认无额外逻辑
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // 超时检查（防止永久存在）
            if (this.tickCount > maxLifetime) {
                this.discard();
                return;
            }

            // 无目标时消失
            if (target == null) {
                this.discard();
                return;
            }

            // 目标死亡或消失时销毁
            if (!target.isAlive() || target.isRemoved()) {
                this.discard();
                return;
            }

            // 未发射状态：等待发射时机
            if (!isShooted()) {
                if (getDeadTick() > 0 && this.tickCount >= getDeadTick() - 1) {
                    this.discard();
                }
                return;
            }

            // 已发射状态：追踪目标
            if (trackingStrength > 0) {
                updateTracking();
            }
        }
    }

    /**
     * 更新追踪逻辑（让魔法剑追踪目标）
     */
    private void updateTracking() {
        if (target == null || !target.isAlive()) {
            return;
        }

        Vec3 currentMotion = this.getDeltaMovement();
        double currentSpeed = currentMotion.length();

        if (currentSpeed < 0.01) {
            return;
        }

        // 计算新的目标方向
        Vec3 targetDirection = calculateDirectionToTarget();
        if (targetDirection == null) {
            return;
        }

        // 归一化方向
        Vec3 normalizedTarget = targetDirection.normalize();
        Vec3 normalizedCurrent = currentMotion.normalize();

        // 平滑插值：当前方向向目标方向偏转
        Vec3 newDirection = normalizedCurrent.lerp(normalizedTarget, trackingStrength);

        // 保持原速度
        this.setDeltaMovement(newDirection.normalize().scale(currentSpeed));

        // 更新实体朝向
        Vec3 motion = this.getDeltaMovement();
        this.setYRot((float) (Math.atan2(motion.x, motion.z) * (180D / Math.PI)));
        this.setXRot((float) (Math.atan2(motion.y, Math.sqrt(motion.x * motion.x + motion.z * motion.z)) * (180D / Math.PI)));
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("shooted", isShooted());
        compound.putInt("deadTick", getDeadTick());
        compound.putFloat("size", getSize());
        compound.putFloat("damage", this.damage);
        compound.putFloat("trackingStrength", this.trackingStrength);
        compound.putInt("maxLifetime", this.maxLifetime);
        compound.putBoolean("hasHit", this.hasHit);
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setShooted(compound.getBoolean("shooted"));
        setDeadTick(compound.getInt("deadTick"));
        setSize(compound.getFloat("size"));
        this.damage = compound.getFloat("damage");
        this.trackingStrength = compound.getFloat("trackingStrength");
        this.maxLifetime = compound.getInt("maxLifetime");
        this.hasHit = compound.getBoolean("hasHit");
    }
}