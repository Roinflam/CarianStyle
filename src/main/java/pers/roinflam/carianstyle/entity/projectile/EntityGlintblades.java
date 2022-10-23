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
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EntityGlintblades extends EntityThrowable {
    public static final String ID = "glintblades";
    public static final String NAME = "glintblades";
    private static final DataParameter<Boolean> SHOOTED = EntityDataManager.createKey(EntityGlintblades.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DEAD_TICK = EntityDataManager.createKey(EntityGlintblades.class, DataSerializers.VARINT);
    private static final DataParameter<Float> SIZE = EntityDataManager.createKey(EntityGlintblades.class, DataSerializers.FLOAT);
    private float damage = 0;
    private Entity target;
    private DamageSource damageSource = DamageSource.MAGIC;

    public EntityGlintblades(@Nonnull World worldIn) {
        super(worldIn);
        setTarget(null);
        this.setNoGravity(true);
        this.setSize(0.75f, 0.75f);
    }

    public EntityGlintblades(@Nonnull EntityLivingBase throwerIn, Entity target) {
        super(throwerIn.world, throwerIn);
        setTarget(target);
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

    public Entity getTarget() {
        return target;
    }

    @Nonnull
    public EntityGlintblades setTarget(Entity target) {
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

    public void shoot(float velocity) {
        if (target != null) {
            setShooted(true);
            @Nonnull Vec3d targetVec = new Vec3d(target.posX - this.posX, (target.posY + (double) target.getEyeHeight()) - this.posY, target.posZ - this.posZ).scale(0.25);
            @Nonnull Vec3d courseVec = new Vec3d(target.motionX, target.motionY, target.motionZ);
            double courseLen = courseVec.lengthVector();
            double targetLen = targetVec.lengthVector();
            double totalLen = Math.sqrt(courseLen * courseLen + targetLen * targetLen);
            @Nonnull Vec3d montion = courseVec.scale(courseLen / totalLen).add(targetVec.scale(targetLen / totalLen));

            this.motionX = montion.x * velocity;
            this.motionY = montion.y * 0.5 + ((getTarget().posY - this.posY) / 2.5 * 0.25) * velocity;
            this.motionZ = montion.z * velocity;
        } else {
            this.setDead();
        }
    }

    @Override
    protected void onImpact(RayTraceResult result) {
        extraAction(result);
        if (getSize() > 5) {
            @Nonnull Explosion explosion = this.world.createExplosion(thrower, this.posX, this.posY, this.posZ, getSize(), false);
        }
        if (result.entityHit instanceof EntityLivingBase) {
            @Nonnull EntityLivingBase entityLivingBase = (EntityLivingBase) result.entityHit;
            if (entityLivingBase.equals(thrower)) {
                return;
            }
            entityLivingBase.hurtResistantTime = entityLivingBase.maxHurtResistantTime / 2;
            entityLivingBase.attackEntityFrom(damageSource, damage);
            entityLivingBase.hurtResistantTime = entityLivingBase.maxHurtResistantTime / 2;
        }
        this.setDead();
    }

    public void extraAction(RayTraceResult result) {

    }

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

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!this.world.isRemote) {
            if (target == null) {
                this.setDead();
            } else if (!isShooted() && getDeadTick() > 0 && this.ticksExisted >= getDeadTick() - 1) {
                this.setDead();
            }
        }
    }
}
