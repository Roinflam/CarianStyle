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
 */
public class EntityGlintblades extends ThrowableProjectile {

    // ==================== 数据参数 ====================

    private static final EntityDataAccessor<Boolean> SHOOTED =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DEAD_TICK =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.FLOAT);

    // ==================== 字段 ====================

    /** 伤害值 */
    private float damage = 0;

    /** 追踪目标 */
    @Nullable
    private Entity target;

    /** 伤害来源 */
    private DamageSource damageSource;

    // ==================== 构造函数 ====================

    /**
     * 用于注册的构造函数
     */
    public EntityGlintblades(@Nonnull EntityType<? extends EntityGlintblades> type, @Nonnull Level level) {
        super(type, level);
        this.target = null;
        this.setNoGravity(true);
        // 1.20.1: DamageSource需要通过DamageSource获取
        this.damageSource = level.damageSources().magic();
    }

    /**
     * 创建实例的便捷构造函数
     */
    public EntityGlintblades(@Nonnull Level level) {
        this(CarianStyleEntity.GLINTBLADES.get(), level);
    }

    /**
     * 从投掷者创建的构造函数
     */
    public EntityGlintblades(@Nonnull LivingEntity thrower, @Nullable Entity target) {
        // 1.20.1: 调用父类构造函数传入EntityType和投掷者
        super(CarianStyleEntity.GLINTBLADES.get(), thrower, thrower.level());
        this.target = target;
        this.setNoGravity(true);
        this.damageSource = thrower.level().damageSources().magic();
    }

    @Override
    protected void defineSynchedData() {
        // 1.20.1: entityInit → defineSynchedData
        // 注意：父类的defineSynchedData已经被调用，这里只注册自己的数据
        this.entityData.define(SHOOTED, false);
        this.entityData.define(DEAD_TICK, 0);
        this.entityData.define(SIZE, 1f);
    }

    // ==================== Getter/Setter ====================

    public float getDamage() {
        return damage;
    }

    @Nonnull
    public EntityGlintblades setDamage(float damage) {
        this.damage = damage;
        return this;
    }

    public DamageSource getDamageSource() {
        return damageSource;
    }

    @Nonnull
    public EntityGlintblades setDamageSource(DamageSource damageSource) {
        this.damageSource = damageSource;
        return this;
    }

    @Nullable
    public Entity getTarget() {
        return target;
    }

    @Nonnull
    public EntityGlintblades setTarget(@Nullable Entity target) {
        this.target = target;
        return this;
    }

    public boolean isShooted() {
        return this.entityData.get(SHOOTED);
    }

    @Nonnull
    private EntityGlintblades setShooted(boolean shooted) {
        this.entityData.set(SHOOTED, shooted);
        return this;
    }

    public int getDeadTick() {
        return this.entityData.get(DEAD_TICK);
    }

    @Nonnull
    public EntityGlintblades setDeadTick(int tick) {
        this.entityData.set(DEAD_TICK, tick);
        return this;
    }

    public float getSize() {
        return this.entityData.get(SIZE);
    }

    @Nonnull
    public EntityGlintblades setSize(float size) {
        this.entityData.set(SIZE, size);
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
            // 1.20.1: setDead → discard
            this.discard();
            return;
        }

        setShooted(true);

        // 计算目标位置（瞄准目标眼睛高度）
        // 1.20.1: posX/posY/posZ → getX()/getY()/getZ()
        double targetX = target.getX() - this.getX();
        double targetY = (target.getY() + target.getEyeHeight()) - this.getY();
        double targetZ = target.getZ() - this.getZ();

        // 预判目标移动
        Vec3 targetVec = new Vec3(targetX, targetY, targetZ).scale(0.25);
        // 1.20.1: motionX/motionY/motionZ → getDeltaMovement()
        Vec3 motionVec = target.getDeltaMovement();

        double motionLen = motionVec.length();
        double targetLen = targetVec.length();
        double totalLen = Math.sqrt(motionLen * motionLen + targetLen * targetLen);

        if (totalLen > 0) {
            Vec3 finalVec = motionVec.scale(motionLen / totalLen)
                    .add(targetVec.scale(targetLen / totalLen));
            // 1.20.1: 设置速度使用 setDeltaMovement
            this.setDeltaMovement(
                    finalVec.x * velocity,
                    finalVec.y * 0.5 + (targetY / 2.5 * 0.25) * velocity,
                    finalVec.z * velocity
            );
        }
    }

    @Override
    protected void onHit(@Nonnull HitResult result) {
        // 1.20.1: onImpact → onHit
        super.onHit(result);

        // 子类可重写的额外逻辑
        extraAction(result);

        // 大型剑产生爆炸
        if (getSize() > 5) {
            // 1.20.1: 爆炸API变化
            this.level().explode(
                    this.getOwner(),
                    this.getX(),
                    this.getY(),
                    this.getZ(),
                    getSize(),
                    Level.ExplosionInteraction.NONE
            );
        }

        this.discard();
    }

    @Override
    protected void onHitEntity(@Nonnull EntityHitResult result) {
        // 1.20.1: 新增专门的实体命中回调
        super.onHitEntity(result);

        Entity hitEntity = result.getEntity();

        // 命中生物造成伤害
        if (hitEntity instanceof LivingEntity) {
            LivingEntity target = (LivingEntity) hitEntity;

            // 不伤害投掷者
            // 1.20.1: thrower → getOwner()
            if (target.equals(this.getOwner())) {
                return;
            }

            // 重置无敌帧后造成伤害
            // 1.20.1: hurtResistantTime → invulnerableTime
            target.invulnerableTime = target.invulnerableDuration / 2;
            target.hurt(damageSource, damage);
            target.invulnerableTime = target.invulnerableDuration / 2;
        }
    }

    /**
     * 命中时的额外逻辑（子类可重写）
     */
    public void extraAction(HitResult result) {
        // 默认无额外逻辑
    }

    @Override
    public void tick() {
        // 1.20.1: onUpdate → tick
        super.tick();

        // 1.20.1: world.isRemote → level().isClientSide
        if (!this.level().isClientSide) {
            // 无目标时消失
            if (target == null) {
                this.discard();
                return;
            }

            // 未发射状态下达到死亡时间时消失
            if (!isShooted() && getDeadTick() > 0 && this.tickCount >= getDeadTick() - 1) {
                this.discard();
            }
        }
    }

    // ==================== NBT ====================

    @Override
    public void addAdditionalSaveData(@Nonnull CompoundTag compound) {
        // 1.20.1: writeEntityToNBT → addAdditionalSaveData
        super.addAdditionalSaveData(compound);
        compound.putBoolean("shooted", isShooted());
        compound.putInt("deadTick", getDeadTick());
        compound.putFloat("size", getSize());
    }

    @Override
    public void readAdditionalSaveData(@Nonnull CompoundTag compound) {
        // 1.20.1: readEntityFromNBT → readAdditionalSaveData
        super.readAdditionalSaveData(compound);
        setShooted(compound.getBoolean("shooted"));
        setDeadTick(compound.getInt("deadTick"));
        setSize(compound.getFloat("size"));
    }
}