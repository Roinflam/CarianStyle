package pers.roinflam.carianstyle.entity.projectile;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
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
 * <h3>v3.0 新增：目标 ID 同步（供客户端实时计算剑尖朝向）</h3>
 * <p>
 * <b>解决的问题：</b>原实现的 {@link #target} 字段<b>只存在于服务端</b>——客户端拿到的实体
 * 该字段恒为 {@code null}，因此渲染器只能靠 {@code getXRot()} / {@code getYRot()} 这两个
 * 「由服务端算好、经 {@code ClientboundMoveEntityPacket} 同步下来」的角度来摆姿势。
 * 而实体注册时 {@code updateInterval(10)} 意味着<b>每 10 tick（0.5 秒）才同步一次</b>，
 * 悬浮期的剑更是压根不更新朝向，于是剑尖既不实时、也不平滑。
 * </p>
 * <p>
 * <b>做法：</b>新增 {@link #TARGET_ID} 同步项，只在<b>设置目标的那一刻</b>下发一次
 * （一个 VarInt，带宽可忽略）。客户端凭此 id 解析出目标实体，
 * 每帧用<b>目标的插值位置</b>自行算方向——完全平滑，与位置同步频率彻底解耦。
 * </p>
 * <p>
 * <b>为什么不保存进 NBT：</b>实体网络 id 在重新载入世界后会重新分配，存下来也解析不到。
 * 而服务端的 {@link #target} 字段本就不参与存档（重进世界后 {@link #tick()} 里
 * {@code target == null} 会直接 discard 掉这把剑），故行为与优化前一致。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
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

    /**
     * 追踪目标的实体网络 id（v3.0 新增）。
     * <p>{@link #NO_TARGET}(-1) 表示无目标。仅供客户端渲染器解析目标位置以实时计算剑尖朝向，
     * <b>不参与任何服务端逻辑判定</b>——服务端一律使用 {@link #target} 字段。</p>
     */
    private static final EntityDataAccessor<Integer> TARGET_ID =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.INT);

    /** 「无目标」哨兵值 */
    public static final int NO_TARGET = -1;

    /**
     * 悬浮锚点：相对<b>释放者</b>的局部偏移（v4.0 新增）。
     * <p>
     * <b>解决的问题：</b>此前剑生成后 {@code setPos} 一次就再也不动了。而老头环里
     * 卡利亚圆阵的辉剑是<b>浮在施法者身周、跟着施法者一起走</b>的——施法者绕后、拉开距离，
     * 整个剑阵都跟着平移，直到延迟结束才齐射出去。钉死在原地会让「剑阵是我召出来的」
     * 这层归属感完全消失。
     * </p>
     * <p>
     * <b>为什么要同步给客户端：</b>如果只在服务端 {@code setPos}，位置变化要走
     * {@code updateInterval}（当前注册为 10 tick）才下发，跟随会一顿一顿。
     * 把偏移同步下去之后，客户端在自己的 {@link #tick()} 里用<b>同一个公式</b>算位置，
     * 与服务端结果完全一致，因此跟随是<b>逐 tick 平滑</b>的，彻底不依赖同步频率。
     * </p>
     * <p>
     * <b>哨兵值：</b>{@code (0,0,0)} 表示「不跟随」（剑钉在生成位置）。
     * 之所以能拿全零当哨兵，是因为偏移全零意味着剑浮在施法者体内——不存在合理用途。
     * 巨剑阵就走这条路：它在持有者<b>死亡瞬间</b>触发，尸体马上消失，没有可跟随的对象。
     * </p>
     */
    private static final EntityDataAccessor<Vector3f> HOVER_OFFSET =
            SynchedEntityData.defineId(EntityGlintblades.class, EntityDataSerializers.VECTOR3);

    // ==================== 字段 ====================

    /**
     * 伤害值
     */
    private float damage = 0;

    /**
     * 追踪目标（仅服务端有效；客户端请用 {@link #getRenderTarget()}）
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

    /**
     * 悬浮期朝向刷新的 tick 计数（v3.0 新增）。
     * <p>悬浮期服务端每 {@link #FACE_TARGET_INTERVAL} tick 才更新一次自身
     * {@code yRot/xRot}，作为「客户端解析不到目标实体」时的兜底姿势
     * （详见 {@link #faceTargetOnServer()}）。</p>
     */
    private int faceTargetCounter = 0;

    /**
     * 最后已知瞄准点 X（v4.1 新增，双端各自维护，不参与同步）。
     * <p>
     * <b>解决的问题：</b>原实现在目标死亡 / 移除时直接 {@code discard()} 掉整把剑——
     * 你打死了怪，半空中那一圈已经蓄好力的辉剑会<b>凭空消失</b>。这既不符合原作
     * （放出去的魔法不会因为目标没了就撤回），也让「补刀时剑阵突然全灭」显得很廉价。
     * </p>
     * <p>
     * <b>做法：</b>每 tick 在目标存活时刷新本坐标；目标一旦死亡 / 卸载，
     * 剑继续朝这个「死亡地点」飞完全程，沿途照常撞人造成伤害，直到命中或寿命耗尽。
     * </p>
     * <p>
     * <b>为什么不同步：</b>双端各自都能解析到目标（服务端用 {@link #target} 字段，
     * 客户端用 {@link #TARGET_ID}），各自缓存的结果收敛到同一个值，
     * 白白多传三个 double 没有意义。存活期间它每 tick 被覆盖，误差上限就是一个 tick 的目标位移。
     * </p>
     */
    private double aimX;
    /** 最后已知瞄准点 Y（含眼高偏移），见 {@link #aimX} */
    private double aimY;
    /** 最后已知瞄准点 Z，见 {@link #aimX} */
    private double aimZ;
    /** 瞄准点是否已被填充过（false 时 {@link #getAimPoint()} 返回 null） */
    private boolean hasAimPoint = false;

    /**
     * 悬浮期服务端朝向刷新间隔（tick）。
     * <p>这只是兜底，客户端正常情况下每帧自算朝向、不依赖它，故取一个很低的频率即可。
     * 悬浮期的剑通常只存在 55~150 tick，5 tick 一次已足够。</p>
     */
    private static final int FACE_TARGET_INTERVAL = 5;

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
        // v3.0：把目标 id 同步给客户端，供渲染器实时计算剑尖朝向
        syncTargetId(target);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(SHOOTED, false);
        this.entityData.define(DEAD_TICK, 0);
        this.entityData.define(SIZE, 1f);
        this.entityData.define(TARGET_ID, NO_TARGET);
        this.entityData.define(HOVER_OFFSET, new Vector3f());
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
     * 获取追踪目标（仅服务端有效）
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
        // v3.0：同步目标 id
        syncTargetId(target);
        return this;
    }

    /**
     * 把目标实体的网络 id 写入同步数据（v3.0 新增）。
     * <p>仅在服务端执行；客户端调用为空操作，避免写入本地副本造成状态不一致。</p>
     *
     * @param target 目标实体，可为 null
     */
    private void syncTargetId(@Nullable Entity target) {
        if (this.level().isClientSide) {
            return;
        }
        this.entityData.set(TARGET_ID, target == null ? NO_TARGET : target.getId());
    }

    /**
     * 取用于<b>渲染</b>的目标实体（双端可用，v3.0 新增）。
     * <p>
     * 服务端直接返回 {@link #target} 字段；客户端凭同步下来的 {@link #TARGET_ID}
     * 从世界里解析。目标已卸载 / 死亡移除时返回 {@code null}，此时渲染器会退回到
     * 「按自身速度方向」或「原地旋转展示」。
     * </p>
     *
     * @return 目标实体；无目标或解析失败时返回 null
     */
    @Nullable
    public Entity getRenderTarget() {
        if (this.target != null) {
            return this.target;
        }
        int id = this.entityData.get(TARGET_ID);
        if (id == NO_TARGET) {
            return null;
        }
        return this.level().getEntity(id);
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

    // ==================== 渲染辅助（双端可用，v3.0 新增）====================

    /**
     * 取本实体的<b>几何中心</b>世界坐标（插值）。
     * <p>{@link #getPosition(float)} 返回的是脚底坐标，而辉剑的碰撞箱是 0.75×0.75 的方块，
     * 渲染与特效都应以中心为基准，故这里统一加上半高。</p>
     *
     * @param partialTicks 帧间插值系数
     * @return 中心世界坐标
     */
    @Nonnull
    public Vec3 getRenderCenter(float partialTicks) {
        // v4.1：挂了锚点就用释放者的插值位置 + 插值 yaw 现算，与释放者渲染同相位、逐帧平滑
        Vec3 anchored = computeAnchorPos(partialTicks);
        Vec3 base = (anchored != null) ? anchored : this.getPosition(partialTicks);
        return base.add(0.0, this.getBbHeight() * 0.5, 0.0);
    }

    /**
     * 计算本帧「剑尖应当指向」的<b>单位方向向量</b>（双端可用，渲染器与特效渲染器共用）。
     * <p>
     * 三级回退，保证任何情况下都有合理姿势：
     * </p>
     * <ol>
     *     <li><b>已发射</b>且速度足够 → 取<b>速度方向</b>。飞行中的剑应当沿飞行轨迹扎出去；
     *         由于 {@link #updateTracking()} 会让速度方向持续偏转向目标，
     *         这本身就等价于「一边飞一边把剑尖拧向目标」，比直接指向目标更自然
     *         （直接指向目标会出现「剑横着平移」的诡异观感）；</li>
     *     <li><b>悬浮期</b>（或已发射但速度接近 0）且能解析到目标 → 取<b>指向目标躯干的方向</b>。
     *         这是卡利亚辉剑最标志性的姿态：一圈剑浮在空中、剑尖齐刷刷锁着你；</li>
     *     <li>两者都不成立 → 返回 {@code null}，由调用方退回到「原地缓慢旋转展示」。</li>
     * </ol>
     * <p>
     * <b>性能：</b>本方法每帧每把剑至多调用两次（本体渲染器 + 特效渲染器各一次），
     * 只做一次向量减法与一次 {@code normalize}（含 1 次 sqrt），无堆分配以外的开销；
     * {@link Vec3} 虽是对象但生命周期极短，与项目其余渲染代码的做法一致。
     * </p>
     *
     * @param partialTicks 帧间插值系数
     * @return 单位方向向量；无法确定时返回 null
     */
    @Nullable
    public Vec3 getAimDirection(float partialTicks) {
        // 1) 飞行中：沿速度方向
        if (isShooted()) {
            Vec3 motion = this.getDeltaMovement();
            if (motion.lengthSqr() > 1.0e-6) {
                return motion.normalize();
            }
        }
        // 2) 悬浮 / 静止：指向目标躯干（瞄准眼高的 80%，与服务端 calculateDirectionToTarget 口径一致）
        Entity aim = getRenderTarget();
        if (aim != null) {
            Vec3 self = getRenderCenter(partialTicks);
            Vec3 targetPos = aim.getPosition(partialTicks)
                    .add(0.0, aim.getEyeHeight() * 0.8, 0.0);
            Vec3 delta = targetPos.subtract(self);
            if (delta.lengthSqr() > 1.0e-6) {
                return delta.normalize();
            }
        }
        // 3) 无法确定
        return null;
    }

    /**
     * 悬浮期的「蓄力进度」（0~1，双端可用，v3.0 新增）。
     * <p>
     * {@code 0} = 刚生成，{@code 1} = 即将发射。渲染器与特效渲染器据此做
     * 「浮现放大 → 符文阵加速旋转 → 临射前增亮」的演出，让玩家能<b>预判剑什么时候飞出来</b>。
     * </p>
     * <p>已发射或未设置 {@code deadTick} 时恒返回 1。</p>
     *
     * @return 蓄力进度 0~1
     */
    public float getChargeProgress() {
        if (isShooted()) {
            return 1f;
        }
        int dead = getDeadTick();
        if (dead <= 0) {
            return 1f;
        }
        return Mth.clamp((float) this.tickCount / (float) dead, 0f, 1f);
    }

    // ==================== 核心逻辑 ====================

    /**
     * 发射魔法剑朝向目标
     *
     * @param velocity 速度倍率
     */
    public void shoot(float velocity) {
        // v4.1：目标已死也照样射出去——瞄准点会退回到最后已知位置（死亡地点）。
        // 只有连一个可瞄的点都没有时才放弃。
        refreshAimPoint();
        if (!hasAimPoint) {
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
        Entity aim = getRenderTarget();
        if (aim != null && aim.isAlive()) {
            // 目标存活：瞄准眼高的 80%，并按其速度做距离相关的预判（至多 5 tick）
            Vec3 targetPos = new Vec3(aim.getX(), aim.getY() + aim.getEyeHeight() * 0.8, aim.getZ());
            Vec3 targetMotion = aim.getDeltaMovement();
            double distance = this.position().distanceTo(targetPos);
            double predictionTime = Math.min(distance * 0.1, 5.0);
            return targetPos.add(targetMotion.scale(predictionTime)).subtract(this.position());
        }
        // v4.1：目标已死 / 已卸载 → 飞向最后已知位置，不做预判（死人不会再移动）
        if (hasAimPoint) {
            return new Vec3(aimX, aimY, aimZ).subtract(this.position());
        }
        return null;
    }

    /**
     * 刷新最后已知瞄准点（双端每 tick 调用）。
     * <p>目标存活时覆盖为其当前躯干位置；目标失效后<b>保持不动</b>，
     * 成为「死亡地点」供剑继续飞向。</p>
     */
    private void refreshAimPoint() {
        Entity aim = getRenderTarget();
        if (aim != null && aim.isAlive()) {
            this.aimX = aim.getX();
            this.aimY = aim.getY() + aim.getEyeHeight() * 0.8;
            this.aimZ = aim.getZ();
            this.hasAimPoint = true;
        }
    }

    /**
     * 显式指定瞄准点（v4.1 新增，供附魔在目标已死时播种死亡地点）。
     * <p>
     * 用途：延迟发射的攻击剑是在延迟到期那一刻<b>新建</b>的，此时若目标已死，
     * 新实体自己从未见过活着的目标、缓存为空。由附魔把「悬浮期记录到的位置」传进来即可。
     * </p>
     *
     * @param point 瞄准点世界坐标
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setAimPoint(@Nullable Vec3 point) {
        if (point != null) {
            this.aimX = point.x;
            this.aimY = point.y;
            this.aimZ = point.z;
            this.hasAimPoint = true;
        }
        return this;
    }

    /**
     * 取最后已知瞄准点。
     *
     * @return 瞄准点；从未记录过时返回 null
     */
    @Nullable
    public Vec3 getAimPoint() {
        return hasAimPoint ? new Vec3(aimX, aimY, aimZ) : null;
    }

    /**
     * 碰撞过滤（v4.1 新增）。
     * <p>
     * <b>两条必须的排除：</b>
     * </p>
     * <ol>
     *     <li><b>悬浮期完全不参与碰撞。</b>剑阵现在浮在释放者身后、贴着人，
     *         而 {@code ThrowableProjectile} 每 tick 都会做射线检测——不排除的话，
     *         剑一生成就撞上释放者的碰撞箱、立刻 {@code onHit} 销毁，整个剑阵会在半帧内消失；</li>
     *     <li><b>永不命中释放者。</b>{@code Projectile.canHitEntity} 默认<b>不</b>排除 owner，
     *         而攻击剑的发射点就在释放者身周 1~2 格——不排除就会出现「自己被自己的剑捅一下」。
     *         原实现靠 {@code onHitEntity} 里的 {@code equals(getOwner())} 兜底跳过伤害，
     *         但那时剑已经 {@code discard} 了，等于一出手就自爆。</li>
     * </ol>
     * <p>
     * 除此之外一律放行——飞行途中撞到任何生物都正常命中并造成伤害，不限于原定目标。
     * </p>
     *
     * @param entity 候选实体
     * @return 允许命中返回 true
     */
    @Override
    protected boolean canHitEntity(@Nonnull Entity entity) {
        if (!isShooted()) {
            return false;
        }
        if (entity.equals(this.getOwner())) {
            return false;
        }
        return super.canHitEntity(entity);
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

        // ⭐ v4.0：悬浮期跟随释放者。刻意放在 isClientSide 判断之外——
        // 双端用同一个公式各算各的，结果一致，故跟随平滑度不受 updateInterval 影响。
        if (!isShooted()) {
            followAnchor();
        }

        // ⭐ v4.1：双端刷新最后已知瞄准点。目标失效后本值定格为死亡地点，剑继续飞过去。
        refreshAimPoint();

        if (!this.level().isClientSide) {
            // 超时检查（防止永久存在）
            if (this.tickCount > maxLifetime) {
                this.discard();
                return;
            }

            // v4.1：只有「连一个可瞄的点都没有」才销毁。
            // 原实现在目标死亡时直接 discard，会导致补刀瞬间整个剑阵凭空消失——
            // 现在目标死了照样朝死亡地点飞完，沿途正常撞人。
            if (!hasAimPoint && getRenderTarget() == null) {
                this.discard();
                return;
            }

            // 未发射状态：等待发射时机
            if (!isShooted()) {
                // v3.0：悬浮期低频朝向目标，作为客户端解析不到目标时的兜底姿势
                faceTargetOnServer();
                if (getDeadTick() > 0 && this.tickCount >= getDeadTick() - 1) {
                    this.discard();
                }
                return;
            }

            // 已发射状态：追踪目标（目标已死时会锁向死亡地点）
            if (trackingStrength > 0) {
                updateTracking();
            }
        }
    }

    /**
     * 悬浮期在服务端低频刷新自身朝向（兜底）。
     *
     * <h4>为什么只是兜底、且刻意做成低频</h4>
     * <p>
     * 渲染朝向由客户端每帧自算（见 {@link #getAimDirection(float)}），<b>不读取</b>
     * {@code getYRot()/getXRot()}。本方法存在的意义只有两个：
     * </p>
     * <ul>
     *     <li>客户端解析不到目标实体（未加载 / 跨区块）时，退回到服务端同步下来的旋转值，
     *         至少姿势不会是默认的竖直；</li>
     *     <li>其它 mod 若读取本实体的旋转做判定，能拿到一个大致正确的值。</li>
     * </ul>
     * <p>
     * 卡利亚圆阵一次 9 把、巨剑阵 3 把，几十名玩家同时战斗时逐 tick 算三角函数是笔纯浪费，
     * 故 {@value #FACE_TARGET_INTERVAL} tick 一次即可——悬浮期通常只有 45~125 tick。
     * </p>
     * <p>v4.1：改用 {@link #calculateDirectionToTarget()}，目标死亡后会自动指向死亡地点。</p>
     */
    private void faceTargetOnServer() {
        if (++faceTargetCounter < FACE_TARGET_INTERVAL) {
            return;
        }
        faceTargetCounter = 0;

        Vec3 direction = calculateDirectionToTarget();
        if (direction == null || direction.lengthSqr() < 1.0e-6) {
            return;
        }
        Vec3 dir = direction.normalize();
        this.setYRot((float) (Math.atan2(dir.x, dir.z) * (180D / Math.PI)));
        this.setXRot((float) (Math.atan2(dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)) * (180D / Math.PI)));
    }

    // ==================== v4.1：悬浮锚点（跟随释放者，朝向相对） ====================

    /**
     * 设置悬浮锚点：让本剑在<b>未发射期间</b>始终浮在释放者身上的某个相对位置。
     * <p>
     * <b>偏移按「右 / 上 / 前」三个局部轴解释</b>（v4.1 由世界轴向改为朝向相对）：
     * {@code x}=右为正，{@code y}=上为正，{@code z}=前为正（负值即身后）。
     * 剑阵要浮在释放者背后，就必须跟着他转身一起转，否则原地转 180° 后剑会跑到脸前挡视线。
     * </p>
     * <p>
     * 传 {@code null} 或零向量表示<b>不跟随</b>（钉在生成位置），这是巨剑阵的用法——
     * 它在持有者死亡瞬间触发，没有可跟随的对象。
     * </p>
     * <p>仅服务端调用有效；客户端凭同步下来的值自行跟随。</p>
     *
     * @param offset 局部偏移（右, 上, 前）；null 表示不跟随
     * @return 当前实例（支持链式调用）
     */
    @Nonnull
    public EntityGlintblades setHoverAnchor(@Nullable Vec3 offset) {
        if (this.level().isClientSide) {
            return this;
        }
        if (offset == null) {
            this.entityData.set(HOVER_OFFSET, new Vector3f());
        } else {
            this.entityData.set(HOVER_OFFSET, new Vector3f((float) offset.x, (float) offset.y, (float) offset.z));
        }
        return this;
    }

    /**
     * 取悬浮锚点的局部偏移（双端可用）。
     *
     * @return 局部偏移（右, 上, 前）；未设置时为零向量
     */
    @Nonnull
    public Vec3 getHoverAnchor() {
        Vector3f v = this.entityData.get(HOVER_OFFSET);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /**
     * 悬浮期跟随释放者（双端每 tick 调用，推进逻辑位置）。
     * <p>
     * 释放者已死亡 / 已卸载时<b>停止跟随、原地悬停</b>：这既是巨剑阵（持有者已死）的正常路径，
     * 也让「施法者中途被打死」有一个合理的收尾——剑阵留在原地把这一轮打完。
     * </p>
     */
    private void followAnchor() {
        Vec3 pos = computeAnchorPos(1.0f);
        if (pos == null) {
            return;
        }
        this.setPos(pos.x, pos.y, pos.z);
        // 悬浮期不应有残余速度，否则 ThrowableProjectile 的移动会与本次 setPos 打架
        this.setDeltaMovement(Vec3.ZERO);
    }

    /**
     * 计算悬浮锚点对应的<b>实体原点（脚底）</b>世界坐标。
     *
     * <h4>为什么渲染时必须传真实的 partialTicks</h4>
     * <p>
     * 这是「转视角 / 移动时剑不够平滑」的<b>根因</b>。上一版只在 {@link #tick()} 里
     * {@code setPos}，而 tick 是 20Hz 的：
     * </p>
     * <ul>
     *     <li>剑的位置取自释放者的<b>tick 位置</b>，但释放者自己是按<b>插值位置</b>渲染的，
     *         两者最多差一整个 tick 的位移。跑动时约 0.2 格，看起来就是剑在人身上「抖」；</li>
     *     <li>转视角更明显——鼠标转动是逐帧的，而剑的方位每 50ms 才跳一次，
     *         快速转身时剑阵会像卡帧一样一格一格甩过去。</li>
     * </ul>
     * <p>
     * 现在渲染路径直接用释放者的<b>插值位置 + 插值 yaw</b>现算锚点
     * （{@link #getRenderCenter(float)} → 本方法），与释放者的渲染完全同相位，
     * 跟随因此是逐帧平滑的，且彻底不依赖 {@code updateInterval}。
     * tick 里则传 {@code 1.0}，负责推进真实的逻辑位置。
     * </p>
     *
     * @param partialTicks 帧间插值系数；逻辑更新传 1.0，渲染传真实值
     * @return 锚点世界坐标；未设锚点或释放者失效时返回 null
     */
    @Nullable
    private Vec3 computeAnchorPos(float partialTicks) {
        Vector3f offset = this.entityData.get(HOVER_OFFSET);
        if (offset.x() == 0f && offset.y() == 0f && offset.z() == 0f) {
            return null;
        }
        Entity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) {
            return null;
        }

        // Minecraft 的 yaw：0 面向 +Z。前向量 =(-sin, 0, cos)，右向量 =(-cos, 0, -sin)
        float yawRad = (float) Math.toRadians(owner.getViewYRot(partialTicks));
        double sin = Math.sin(yawRad);
        double cos = Math.cos(yawRad);

        double localRight = offset.x();
        double localUp = offset.y();
        double localForward = offset.z();

        Vec3 base = owner.getPosition(partialTicks);
        return new Vec3(
                base.x + (-cos) * localRight + (-sin) * localForward,
                base.y + localUp,
                base.z + (-sin) * localRight + cos * localForward
        );
    }

    /**
     * 更新追踪逻辑（让魔法剑追踪目标）
     */
    private void updateTracking() {
        // v4.1：不再要求目标存活——calculateDirectionToTarget 会退回死亡地点

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
        // 注意：targetId 刻意不存档——实体网络 id 在重新载入后会重新分配，存下来也解析不到；
        // 而服务端的 target 字段本就不参与存档，重进世界后 tick() 会因 target == null 直接销毁本实体。
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
