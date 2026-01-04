package pers.roinflam.carianstyle.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

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
public class EntityGlintblades extends EntityThrowable {

    public static final String ID = "glintblades";
    public static final String NAME = "glintblades";

    private static final DataParameter<Boolean> SHOOTED = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DEAD_TICK = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.VARINT);
    private static final DataParameter<Float> SIZE = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.FLOAT);

    /** 伤害值 */
    private float damage = 0;

    /** 追踪目标 */
    @Nullable
    private Entity target;

    /** 伤害来源 */
    private DamageSource damageSource = DamageSource.MAGIC;

    public EntityGlintblades(@Nonnull World worldIn) {
        super(worldIn);
        this.target = null;
        this.setNoGravity(true);
        this.setSize(0.75f, 0.75f);
    }

    public EntityGlintblades(@Nonnull EntityLivingBase throwerIn, @Nullable Entity target) {
        super(throwerIn.world, throwerIn);
        this.target = target;
        this.setNoGravity(true);
        this.setSize(0.75f, 0.75f);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(SHOOTED, false);
        dataManager.register(DEAD_TICK, 0);
        dataManager.register(SIZE, 1f);
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
        return dataManager.get(SHOOTED);
    }

    @Nonnull
    private EntityGlintblades setShooted(boolean shooted) {
        dataManager.set(SHOOTED, shooted);
        return this;
    }

    public int getDeadTick() {
        return dataManager.get(DEAD_TICK);
    }

    @Nonnull
    public EntityGlintblades setDeadTick(int tick) {
        dataManager.set(DEAD_TICK, tick);
        return this;
    }

    public float getSize() {
        return dataManager.get(SIZE);
    }

    @Nonnull
    public EntityGlintblades setSize(float size) {
        dataManager.set(SIZE, size);
        return this;
    }

    // ==================== 核心逻辑 ====================

    /**
     * 发射魔法剑朝向目标
     *
     * @param velocity 速度倍率
     */
    public void shoot(float velocity) {
        if (target == null || !target.isEntityAlive()) {
            this.setDead();
            return;
        }

        setShooted(true);

        // 计算目标位置（瞄准目标眼睛高度）
        double targetX = target.posX - this.posX;
        double targetY = (target.posY + target.getEyeHeight()) - this.posY;
        double targetZ = target.posZ - this.posZ;

        // 预判目标移动
        Vec3d targetVec = new Vec3d(targetX, targetY, targetZ).scale(0.25);
        Vec3d motionVec = new Vec3d(target.motionX, target.motionY, target.motionZ);

        double motionLen = motionVec.lengthVector();
        double targetLen = targetVec.lengthVector();
        double totalLen = Math.sqrt(motionLen * motionLen + targetLen * targetLen);

        if (totalLen > 0) {
            Vec3d finalVec = motionVec.scale(motionLen / totalLen).add(targetVec.scale(targetLen / totalLen));
            this.motionX = finalVec.x * velocity;
            this.motionY = finalVec.y * 0.5 + (targetY / 2.5 * 0.25) * velocity;
            this.motionZ = finalVec.z * velocity;
        }
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        // 子类可重写的额外逻辑
        extraAction(result);

        // 大型剑产生爆炸
        if (getSize() > 5) {
            this.world.createExplosion(thrower, this.posX, this.posY, this.posZ, getSize(), false);
        }

        // 命中生物造成伤害
        if (result.entityHit instanceof EntityLivingBase) {
            EntityLivingBase target = (EntityLivingBase) result.entityHit;

            // 不伤害投掷者
            if (target.equals(thrower)) {
                return;
            }

            // 重置无敌帧后造成伤害
            target.hurtResistantTime = target.maxHurtResistantTime / 2;
            target.attackEntityFrom(damageSource, damage);
            target.hurtResistantTime = target.maxHurtResistantTime / 2;
        }

        this.setDead();
    }

    /**
     * 命中时的额外逻辑（子类可重写）
     */
    public void extraAction(RayTraceResult result) {
        // 默认无额外逻辑
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!this.world.isRemote) {
            // 无目标时消失
            if (target == null) {
                this.setDead();
                return;
            }

            // 未发射状态下达到死亡时间时消失
            if (!isShooted() && getDeadTick() > 0 && this.ticksExisted >= getDeadTick() - 1) {
                this.setDead();
            }
        }
    }

    // ==================== NBT ====================

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("shooted", isShooted());
        compound.setInteger("deadTick", getDeadTick());
        compound.setFloat("size", getSize());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        setShooted(compound.getBoolean("shooted"));
        setDeadTick(compound.getInteger("deadTick"));
        setSize(compound.getFloat("size"));
    }
}