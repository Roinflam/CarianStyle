package pers.roinflam.carianstyle.entity.projectile;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * 魔法剑实体
 * <p>
 * 卡利亚圆阵、卡利亚式奉还、巨剑阵等附魔共用的投射物。
 * 特性：
 * - 发射后持续追踪目标
 * - 可设置延迟发射（悬浮期），悬浮期可以锚定在释放者身上、跟着释放者移动和转身
 * - 可自定义大小和伤害，大型剑命中时爆炸
 * </p>
 *
 * <h3>目标死了也照样飞</h3>
 * <p>
 * 以前发射那一刻目标已死，整把剑直接消失：箭把怪射死的那一下，整套剑阵一把都飞不出去。
 * 现在每 tick 记下目标躯干的位置，目标死亡 / 卸载后剑朝这个「死亡地点」飞完，沿途照常撞人，
 * 只有连一个可瞄的点都没有时才销毁。附魔也可以用 {@link #setAimPoint} 直接给一个瞄准点。
 * </p>
 *
 * <h3>移动与碰撞自己做</h3>
 * <p>
 * 不走 {@code EntityThrowable} 自带的移动：它悬浮期也做碰撞，剑浮在释放者身边会一生成就撞上释放者；
 * 它对投掷者只在出手头几 tick 豁免，之后自己的剑能捅到自己。这里悬浮期完全不参与碰撞，
 * 飞行中永不命中释放者，方块碰撞只看有碰撞箱的方块（草、花不挡剑）。碰撞只在服务端判定。
 * </p>
 *
 * <h3>寿命</h3>
 * <p>默认 200 tick（含悬浮期），各附魔可改；以前没有上限，射偏了会一直飞到撞上东西为止。</p>
 *
 * <h3>目标 id 同步（供客户端实时计算剑尖朝向）</h3>
 * <p>
 * {@link #target} 字段<b>只存在于服务端</b>，客户端拿到的实体该字段恒为 {@code null}。
 * 实体注册的同步间隔是 10 tick，悬浮期的剑更是不更新朝向，只靠同步下来的角度摆姿势，剑尖既不实时、也不平滑。
 * 所以把目标 id 放进 {@link #TARGET_ID}，只在<b>设置目标的那一刻</b>下发一次；客户端凭它解析出目标实体，
 * 每帧用<b>目标的插值位置</b>自行算方向（{@link #getAimDirection}），和位置同步频率彻底解耦。
 * 实体 id 重进世界后会重新分配，不存档；服务端的 {@link #target} 也不存档，重进世界后这把剑会被直接销毁。
 * </p>
 *
 * <h3>和 1.20.1 的差别</h3>
 * <ul>
 *     <li>1.12.2 版：继承 {@code EntityThrowable}（1.20.1 是 {@code ThrowableProjectile}），移动和碰撞自己写（见上）。</li>
 *     <li>1.12.2 版：{@code EntityThrowable} 的投掷者只在服务端有，另用 {@link #OWNER_ID} 把释放者 id 同步给客户端，
 *         {@link #getOwner()} 两端都能用（1.20.1 的 {@code Projectile} 自己会同步）。</li>
 *     <li>1.12.2 版：悬浮锚点拆成三个 float 同步（1.12.2 没有 {@code Vector3f} 的同步序列化器）。</li>
 *     <li>1.12.2 版：多部位生物的部位是 {@code MultiPartEntityPart}，伤害转给 {@code parent}（1.20.1 是 {@code PartEntity.getParent()}）。</li>
 *     <li>1.12.2 版：多一个 {@link #getRenderOffset}：1.12.2 的实体渲染器拿到的是实体自己的插值坐标，
 *         悬浮跟随时要按释放者的插值位置补一段偏移。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.0
 */
public class EntityGlintblades extends EntityThrowable {

    /** 「无目标 / 无释放者」哨兵值 */
    public static final int NO_TARGET = -1;

    private static final DataParameter<Boolean> SHOOTED = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.BOOLEAN);
    private static final DataParameter<Integer> DEAD_TICK = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.VARINT);
    private static final DataParameter<Float> SIZE = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.FLOAT);

    /**
     * 目标的实体 id，只在设目标时下发一次，客户端靠它找到目标（算死亡地点、悬浮期朝向）
     * <p>实体 id 重进世界会重新分配，不存档。</p>
     */
    private static final DataParameter<Integer> TARGET_ID = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.VARINT);

    /**
     * 释放者的实体 id
     * <p>{@code EntityThrowable} 的投掷者字段只在服务端有，客户端要靠它算悬浮锚点。</p>
     */
    private static final DataParameter<Integer> OWNER_ID = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.VARINT);

    /**
     * 悬浮锚点：相对释放者的局部偏移（右 / 上 / 前）
     * <p>
     * 同步给客户端后，两端每 tick 用同一个公式各算各的位置，跟随是逐 tick 平滑的，
     * 不依赖位置同步的频率（注册的是每 10 tick 一次）。全零表示不跟随，钉在生成位置。
     * </p>
     */
    private static final DataParameter<Float> HOVER_RIGHT = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> HOVER_UP = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.FLOAT);
    private static final DataParameter<Float> HOVER_FORWARD = EntityDataManager.createKey(
            EntityGlintblades.class, DataSerializers.FLOAT);

    /** 悬浮期服务端刷新朝向的间隔（tick），只是兜底姿势，低频即可 */
    private static final int FACE_TARGET_INTERVAL = 5;

    /** 伤害值 */
    private float damage = 0;

    /** 追踪目标（只在服务端有，客户端用 {@link #getRenderTarget()}） */
    @Nullable
    private Entity target;

    /** 伤害来源 */
    private DamageSource damageSource = DamageSource.MAGIC;

    /** 追踪强度（0~1，越大转向越快） */
    private float trackingStrength = 0.1F;

    /** 最长存活时间（tick，含悬浮期） */
    private int maxLifetime = 200;

    /** 已经命中过，防止同一 tick 重复结算 */
    private boolean hasHit = false;

    /** 悬浮期朝向刷新计数 */
    private int faceTargetCounter = 0;

    /** 最后已知瞄准点（目标躯干，眼高 80%）；目标失效后定格为死亡地点。两端各自维护，不同步 */
    private double aimX;
    private double aimY;
    private double aimZ;
    private boolean hasAimPoint = false;

    /**
     * 注册 / 客户端生成 / 读存档用的构造器
     *
     * @param worldIn 世界
     */
    public EntityGlintblades(@Nonnull World worldIn) {
        super(worldIn);
        this.target = null;
        this.setNoGravity(true);
        this.setSize(0.75f, 0.75f);
    }

    /**
     * 附魔放剑用的构造器
     *
     * @param throwerIn 释放者
     * @param target    追踪目标；可为 null
     */
    public EntityGlintblades(@Nonnull EntityLivingBase throwerIn, @Nullable Entity target) {
        super(throwerIn.world, throwerIn);
        this.target = target;
        this.setNoGravity(true);
        this.setSize(0.75f, 0.75f);
        if (!this.world.isRemote) {
            this.dataManager.set(OWNER_ID, throwerIn.getEntityId());
            this.dataManager.set(TARGET_ID, target == null ? NO_TARGET : target.getEntityId());
        }
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataManager.register(SHOOTED, false);
        dataManager.register(DEAD_TICK, 0);
        dataManager.register(SIZE, 1f);
        dataManager.register(TARGET_ID, NO_TARGET);
        dataManager.register(OWNER_ID, NO_TARGET);
        dataManager.register(HOVER_RIGHT, 0f);
        dataManager.register(HOVER_UP, 0f);
        dataManager.register(HOVER_FORWARD, 0f);
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
        if (!this.world.isRemote) {
            this.dataManager.set(TARGET_ID, target == null ? NO_TARGET : target.getEntityId());
        }
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

    /**
     * 追踪强度
     *
     * @param strength 0~1，越大转向越快；0 表示直线飞
     * @return 当前实例
     */
    @Nonnull
    public EntityGlintblades setTrackingStrength(float strength) {
        this.trackingStrength = Math.max(0, Math.min(1, strength));
        return this;
    }

    /**
     * 最长存活时间
     *
     * @param ticks tick 数（含悬浮期）
     * @return 当前实例
     */
    @Nonnull
    public EntityGlintblades setMaxLifetime(int ticks) {
        this.maxLifetime = ticks;
        return this;
    }

    /**
     * 释放者（两端可用）
     *
     * @return 释放者；不在了返回 null
     */
    @Nullable
    public Entity getOwner() {
        Entity owner = getThrower();
        if (owner != null) {
            return owner;
        }
        int id = dataManager.get(OWNER_ID);
        return id == NO_TARGET ? null : world.getEntityByID(id);
    }

    /**
     * 目标（两端可用）：服务端直接用字段，客户端按同步下来的 id 找
     *
     * @return 目标；没有或找不到时返回 null
     */
    @Nullable
    public Entity getRenderTarget() {
        if (this.target != null) {
            return this.target;
        }
        int id = dataManager.get(TARGET_ID);
        return id == NO_TARGET ? null : world.getEntityByID(id);
    }

    // ==================== 瞄准 ====================

    /**
     * 发射：朝目标（目标已死则朝死亡地点）飞出去
     *
     * @param velocity 速度
     */
    public void shoot(float velocity) {
        refreshAimPoint();
        if (!hasAimPoint) {
            this.setDead();
            return;
        }

        setShooted(true);

        Vec3d direction = calculateDirectionToTarget();
        if (direction != null && direction.lengthSquared() > 1.0E-6D) {
            Vec3d motion = direction.normalize().scale(velocity);
            this.motionX = motion.x;
            this.motionY = motion.y;
            this.motionZ = motion.z;
            faceMotion();
        }
    }

    /**
     * 指向目标的方向（带预判）
     * <p>
     * 目标活着：瞄眼高的 80%，按它的速度做距离相关的预判（至多 5 tick）；
     * 目标没了：飞向最后已知位置，不预判。
     * </p>
     */
    @Nullable
    private Vec3d calculateDirectionToTarget() {
        Entity aim = getRenderTarget();
        Vec3d self = new Vec3d(posX, posY, posZ);
        if (aim != null && aim.isEntityAlive()) {
            Vec3d targetPos = new Vec3d(aim.posX, aim.posY + aim.getEyeHeight() * 0.8D, aim.posZ);
            double predictionTime = Math.min(self.distanceTo(targetPos) * 0.1D, 5.0D);
            return targetPos.add(new Vec3d(aim.motionX, aim.motionY, aim.motionZ).scale(predictionTime)).subtract(self);
        }
        if (hasAimPoint) {
            return new Vec3d(aimX, aimY, aimZ).subtract(self);
        }
        return null;
    }

    /**
     * 目标活着时把瞄准点刷新成它当前的躯干位置；目标失效后保持不动
     */
    private void refreshAimPoint() {
        Entity aim = getRenderTarget();
        if (aim != null && aim.isEntityAlive()) {
            this.aimX = aim.posX;
            this.aimY = aim.posY + aim.getEyeHeight() * 0.8D;
            this.aimZ = aim.posZ;
            this.hasAimPoint = true;
        }
    }

    /**
     * 直接指定瞄准点
     * <p>延迟发射的剑是到点才新建的，那时目标可能已经死了，由附魔把记下的位置传进来。</p>
     *
     * @param point 瞄准点；null 不改
     * @return 当前实例
     */
    @Nonnull
    public EntityGlintblades setAimPoint(@Nullable Vec3d point) {
        if (point != null) {
            this.aimX = point.x;
            this.aimY = point.y;
            this.aimZ = point.z;
            this.hasAimPoint = true;
        }
        return this;
    }

    /**
     * 最后已知瞄准点
     *
     * @return 瞄准点；从没记录过时为 null
     */
    @Nullable
    public Vec3d getAimPoint() {
        return hasAimPoint ? new Vec3d(aimX, aimY, aimZ) : null;
    }

    // ==================== 悬浮锚点 ====================

    /**
     * 设置悬浮锚点：未发射期间一直浮在释放者身上的这个相对位置，跟着他走、跟着他转身
     * <p>
     * 偏移按释放者的「右 / 上 / 前」解释，负的前向即身后。
     * 传 null 或零向量表示不跟随（巨剑阵在持有者死亡时触发，没有可跟随的对象）。
     * 只在服务端设置，客户端凭同步值自己算。
     * </p>
     *
     * @param offset 局部偏移（右, 上, 前）
     * @return 当前实例
     */
    @Nonnull
    public EntityGlintblades setHoverAnchor(@Nullable Vec3d offset) {
        if (this.world.isRemote) {
            return this;
        }
        dataManager.set(HOVER_RIGHT, offset == null ? 0f : (float) offset.x);
        dataManager.set(HOVER_UP, offset == null ? 0f : (float) offset.y);
        dataManager.set(HOVER_FORWARD, offset == null ? 0f : (float) offset.z);
        return this;
    }

    /**
     * 悬浮锚点对应的世界坐标（实体原点）
     * <p>
     * 释放者死了 / 不在了返回 null，剑原地悬停把这一轮打完。
     * 逻辑更新传 1.0；渲染传真实的 partialTicks，用释放者的插值位置和插值朝向现算，
     * 和释放者本身的渲染同相位，跑动、转视角时剑不会一顿一顿。
     * </p>
     *
     * @param partialTicks 帧间插值
     * @return 世界坐标；不跟随时为 null
     */
    @Nullable
    public Vec3d computeAnchorPos(float partialTicks) {
        float right = dataManager.get(HOVER_RIGHT);
        float up = dataManager.get(HOVER_UP);
        float forward = dataManager.get(HOVER_FORWARD);
        if (right == 0f && up == 0f && forward == 0f) {
            return null;
        }
        Entity owner = getOwner();
        if (owner == null || !owner.isEntityAlive()) {
            return null;
        }

        // yaw 0 面向 +Z：前向量 (-sin, 0, cos)，右向量 (-cos, 0, -sin)
        float yaw = owner.prevRotationYaw + (owner.rotationYaw - owner.prevRotationYaw) * partialTicks;
        double yawRad = Math.toRadians(yaw);
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double baseX = owner.lastTickPosX + (owner.posX - owner.lastTickPosX) * partialTicks;
        double baseY = owner.lastTickPosY + (owner.posY - owner.lastTickPosY) * partialTicks;
        double baseZ = owner.lastTickPosZ + (owner.posZ - owner.lastTickPosZ) * partialTicks;
        return new Vec3d(
                baseX + (-cos) * right + (-sin) * forward,
                baseY + up,
                baseZ + (-sin) * right + cos * forward
        );
    }

    /**
     * 渲染时相对插值位置要补的偏移（悬浮跟随时用释放者的插值位置现算）
     *
     * @param partialTicks 帧间插值
     * @return 偏移；不需要补时为 null
     */
    @Nullable
    public Vec3d getRenderOffset(float partialTicks) {
        if (isShooted()) {
            return null;
        }
        Vec3d anchored = computeAnchorPos(partialTicks);
        if (anchored == null) {
            return null;
        }
        return new Vec3d(
                anchored.x - (lastTickPosX + (posX - lastTickPosX) * partialTicks),
                anchored.y - (lastTickPosY + (posY - lastTickPosY) * partialTicks),
                anchored.z - (lastTickPosZ + (posZ - lastTickPosZ) * partialTicks)
        );
    }

    /**
     * 悬浮锚点（局部偏移：右, 上, 前）
     *
     * @return 偏移；全零表示不跟随
     */
    @Nonnull
    public Vec3d getHoverAnchor() {
        return new Vec3d(dataManager.get(HOVER_RIGHT), dataManager.get(HOVER_UP), dataManager.get(HOVER_FORWARD));
    }

    // ==================== 渲染辅助（双端可用）====================

    /**
     * 渲染中心：插值位置再抬半个身高
     * <p>挂了锚点就用释放者的插值位置 + 插值朝向现算，和释放者的渲染同相位、逐帧平滑。</p>
     *
     * @param partialTicks 帧间插值
     * @return 世界坐标
     */
    @Nonnull
    public Vec3d getRenderCenter(float partialTicks) {
        Vec3d anchored = isShooted() ? null : computeAnchorPos(partialTicks);
        double x;
        double y;
        double z;
        if (anchored != null) {
            x = anchored.x;
            y = anchored.y;
            z = anchored.z;
        } else {
            x = lastTickPosX + (posX - lastTickPosX) * partialTicks;
            y = lastTickPosY + (posY - lastTickPosY) * partialTicks;
            z = lastTickPosZ + (posZ - lastTickPosZ) * partialTicks;
        }
        return new Vec3d(x, y + this.height * 0.5D, z);
    }

    /**
     * 剑尖朝向（单位向量）
     * <ol>
     *     <li>飞行中：沿速度方向；</li>
     *     <li>悬浮 / 静止：指向目标躯干（眼高的 80%，和服务端 {@link #calculateDirectionToTarget} 的口径一致），
     *         用目标的插值位置，逐帧平滑；</li>
     *     <li>都定不下来：返回 null，渲染器退回实体自己的角度。</li>
     * </ol>
     *
     * @param partialTicks 帧间插值
     * @return 方向；无法确定时为 null
     */
    @Nullable
    public Vec3d getAimDirection(float partialTicks) {
        if (isShooted()) {
            double lenSqr = motionX * motionX + motionY * motionY + motionZ * motionZ;
            if (lenSqr > 1.0E-6D) {
                return new Vec3d(motionX, motionY, motionZ).normalize();
            }
        }
        Entity aim = getRenderTarget();
        if (aim != null) {
            Vec3d self = getRenderCenter(partialTicks);
            double tx = aim.lastTickPosX + (aim.posX - aim.lastTickPosX) * partialTicks;
            double ty = aim.lastTickPosY + (aim.posY - aim.lastTickPosY) * partialTicks + aim.getEyeHeight() * 0.8D;
            double tz = aim.lastTickPosZ + (aim.posZ - aim.lastTickPosZ) * partialTicks;
            Vec3d delta = new Vec3d(tx - self.x, ty - self.y, tz - self.z);
            if (delta.lengthSquared() > 1.0E-6D) {
                return delta.normalize();
            }
        }
        return null;
    }

    /**
     * 悬浮蓄力进度：存活 tick / 悬浮总时长，夹在 0~1；已发射或没有悬浮期时为 1
     *
     * @return 0~1
     */
    public float getChargeProgress() {
        if (isShooted()) {
            return 1F;
        }
        int dead = getDeadTick();
        if (dead <= 0) {
            return 1F;
        }
        return MathHelper.clamp((float) this.ticksExisted / (float) dead, 0F, 1F);
    }

    private void followAnchor() {
        Vec3d pos = computeAnchorPos(1.0F);
        if (pos == null) {
            return;
        }
        this.setPosition(pos.x, pos.y, pos.z);
        this.motionX = 0;
        this.motionY = 0;
        this.motionZ = 0;
    }

    // ==================== 更新 ====================

    @Override
    public void onUpdate() {
        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;
        // 只做 Entity 的基础更新（上一 tick 位置、火、水、出界），移动和碰撞自己来
        this.onEntityUpdate();
        if (this.isDead) {
            return;
        }

        if (isShooted()) {
            moveAndCollide();
            if (this.isDead) {
                return;
            }
        } else {
            followAnchor();
        }

        refreshAimPoint();

        if (this.world.isRemote) {
            return;
        }

        if (this.ticksExisted > maxLifetime) {
            this.setDead();
            return;
        }

        // 连一个可瞄的点都没有才销毁；目标死了照样朝死亡地点飞
        if (!hasAimPoint && getRenderTarget() == null) {
            this.setDead();
            return;
        }

        if (!isShooted()) {
            faceTargetOnServer();
            if (getDeadTick() > 0 && this.ticksExisted >= getDeadTick() - 1) {
                this.setDead();
            }
            return;
        }

        if (trackingStrength > 0) {
            updateTracking();
        }
    }

    /**
     * 按当前速度移动一格；服务端先沿路径找方块和实体，撞上就结算
     */
    private void moveAndCollide() {
        if (!this.world.isRemote) {
            Vec3d start = new Vec3d(posX, posY, posZ);
            Vec3d end = new Vec3d(posX + motionX, posY + motionY, posZ + motionZ);
            // 只看有碰撞箱的方块：草、花、火把不挡剑
            RayTraceResult result = this.world.rayTraceBlocks(start, end, false, true, false);
            if (result != null) {
                end = new Vec3d(result.hitVec.x, result.hitVec.y, result.hitVec.z);
            }

            Entity hitEntity = findEntityOnPath(start, end);
            if (hitEntity != null) {
                result = new RayTraceResult(hitEntity);
            }

            if (result != null && result.typeOfHit != RayTraceResult.Type.MISS
                    && !ForgeEventFactory.onProjectileImpact(this, result)) {
                onImpact(result);
            }
            if (this.isDead) {
                return;
            }
        }

        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;
        faceMotion();

        float drag = this.isInWater() ? 0.8F : 0.99F;
        this.motionX *= drag;
        this.motionY *= drag;
        this.motionZ *= drag;
        this.setPosition(this.posX, this.posY, this.posZ);
    }

    @Nullable
    private Entity findEntityOnPath(@Nonnull Vec3d start, @Nonnull Vec3d end) {
        AxisAlignedBB searchBox = this.getEntityBoundingBox().expand(motionX, motionY, motionZ).grow(1.0D);
        List<Entity> candidates = this.world.getEntitiesWithinAABBExcludingEntity(this, searchBox);
        Entity closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Entity candidate : candidates) {
            if (!canHitEntity(candidate)) {
                continue;
            }
            RayTraceResult intercept = candidate.getEntityBoundingBox().grow(0.3D).calculateIntercept(start, end);
            if (intercept == null) {
                continue;
            }
            double distance = start.squareDistanceTo(intercept.hitVec);
            if (distance < closestDistance) {
                closest = candidate;
                closestDistance = distance;
            }
        }
        return closest;
    }

    /**
     * 碰撞过滤：悬浮期不撞任何东西，永不撞释放者（包括释放者的部位实体）
     */
    private boolean canHitEntity(@Nonnull Entity entity) {
        if (!isShooted() || !entity.canBeCollidedWith()) {
            return false;
        }
        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) {
            return false;
        }
        Entity owner = getOwner();
        if (owner == null) {
            return true;
        }
        if (entity.equals(owner)) {
            return false;
        }
        return !(entity instanceof MultiPartEntityPart && owner.equals(((MultiPartEntityPart) entity).parent));
    }

    /**
     * 飞行中逐 tick 把速度方向往目标方向偏一点，速度大小不变
     * <p>目标死了会锁向死亡地点。</p>
     */
    private void updateTracking() {
        double speed = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
        if (speed < 0.01D) {
            return;
        }

        Vec3d targetDirection = calculateDirectionToTarget();
        if (targetDirection == null || targetDirection.lengthSquared() < 1.0E-6D) {
            return;
        }

        Vec3d to = targetDirection.normalize();
        Vec3d from = new Vec3d(motionX / speed, motionY / speed, motionZ / speed);
        Vec3d blended = new Vec3d(
                from.x + (to.x - from.x) * trackingStrength,
                from.y + (to.y - from.y) * trackingStrength,
                from.z + (to.z - from.z) * trackingStrength
        );
        if (blended.lengthSquared() < 1.0E-6D) {
            return;
        }
        Vec3d motion = blended.normalize().scale(speed);
        this.motionX = motion.x;
        this.motionY = motion.y;
        this.motionZ = motion.z;
        faceMotion();
    }

    /**
     * 悬浮期低频刷新朝向，供拿不到目标的客户端和别的模组读个大致方向
     */
    private void faceTargetOnServer() {
        if (++faceTargetCounter < FACE_TARGET_INTERVAL) {
            return;
        }
        faceTargetCounter = 0;

        Vec3d direction = calculateDirectionToTarget();
        if (direction == null || direction.lengthSquared() < 1.0E-6D) {
            return;
        }
        Vec3d dir = direction.normalize();
        this.rotationYaw = (float) (MathHelper.atan2(dir.x, dir.z) * (180D / Math.PI));
        this.rotationPitch = (float) (MathHelper.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180D / Math.PI));
    }

    private void faceMotion() {
        float horizontal = MathHelper.sqrt(motionX * motionX + motionZ * motionZ);
        this.rotationYaw = (float) (MathHelper.atan2(motionX, motionZ) * (180D / Math.PI));
        this.rotationPitch = (float) (MathHelper.atan2(motionY, horizontal) * (180D / Math.PI));
    }

    // ==================== 命中 ====================

    @Override
    protected void onImpact(@Nonnull RayTraceResult result) {
        if (hasHit) {
            return;
        }
        hasHit = true;

        if (result.typeOfHit == RayTraceResult.Type.ENTITY && result.entityHit != null) {
            damageHitEntity(result.entityHit);
        }

        // 子类可重写的额外逻辑
        extraAction(result);

        // 大型剑产生爆炸
        if (getSize() > 5) {
            this.world.createExplosion(getOwner(), this.posX, this.posY, this.posZ, getSize(), false);
        }

        this.setDead();
    }

    /**
     * 对撞上的实体结算伤害
     * <p>
     * 多部位生物（末影龙、一些模组的 BOSS）撞到的是部位实体，不是生物，以前这一剑什么也不做；
     * 现在交给部位转给本体（{@code attackEntityFromPart}），无敌帧按本体算。
     * 结算前清掉无敌帧，打中后把无敌帧压到至多一半，不挡后面别的伤害。
     * </p>
     */
    private void damageHitEntity(@Nonnull Entity hit) {
        Entity body = hit;
        if (hit instanceof MultiPartEntityPart && ((MultiPartEntityPart) hit).parent instanceof Entity) {
            body = (Entity) ((MultiPartEntityPart) hit).parent;
        }

        // 不伤害投掷者
        if (body.equals(getOwner())) {
            return;
        }

        if (!(body instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase living = (EntityLivingBase) body;
        int oldInvulnerableTime = living.hurtResistantTime;
        living.hurtResistantTime = 0;

        boolean damaged = hit.attackEntityFrom(damageSource, damage);

        if (damaged) {
            living.hurtResistantTime = Math.min(oldInvulnerableTime, living.maxHurtResistantTime / 2);
        }
    }

    /**
     * 命中时的额外逻辑（子类可重写）
     */
    public void extraAction(RayTraceResult result) {
        // 默认无额外逻辑
    }

    // ==================== NBT ====================

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean("shooted", isShooted());
        compound.setInteger("deadTick", getDeadTick());
        compound.setFloat("size", getSize());
        compound.setFloat("damage", this.damage);
        compound.setFloat("trackingStrength", this.trackingStrength);
        compound.setInteger("maxLifetime", this.maxLifetime);
        compound.setBoolean("hasHit", this.hasHit);
        // 目标 id 不存：实体 id 重进世界后会重新分配，目标字段也不存，读回来后 onUpdate 里会直接销毁
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        setShooted(compound.getBoolean("shooted"));
        setDeadTick(compound.getInteger("deadTick"));
        setSize(compound.getFloat("size"));
        this.damage = compound.getFloat("damage");
        this.trackingStrength = compound.getFloat("trackingStrength");
        this.maxLifetime = compound.getInteger("maxLifetime");
        this.hasHit = compound.getBoolean("hasHit");
    }
}
