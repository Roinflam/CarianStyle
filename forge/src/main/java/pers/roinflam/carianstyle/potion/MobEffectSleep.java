package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 睡眠药水效果
 * <p>
 * 效果：
 * - 移动速度降为0（无法移动）
 * - 无法跳跃
 * - 生物无法设置攻击目标
 * - 无法攻击其他实体
 * - 受到攻击时伤害×2 + 伤害×等级×25%，并解除睡眠
 * - 持续施加失明效果
 * </p>
 *
 * <h3>v1.1 修复：客户端重复施加失明导致的效果不同步</h3>
 * <p>
 * <b>问题：</b>{@link #applyEffectTick} 此前缺少客户端守卫。原版
 * {@code LivingEntity.tickEffects()} 在<b>逻辑服务端与逻辑客户端两侧都会执行</b>
 * （单人游戏亦然——单机是「一个进程内同时跑客户端与内置服务端」），
 * 因此本方法内的 {@code addEffect(BLINDNESS)} 每 tick 在客户端也会被调用一次。
 * </p>
 * <p>
 * 后果：
 * <ul>
 *     <li><b>效果不同步</b>——客户端自行往本地效果表里塞失明，其时长与服务端下发的版本
 *         不一致；服务端的效果同步包到达时会覆盖，两者反复打架，可能造成失明画面闪烁；</li>
 *     <li><b>无谓开销</b>——每秒 20 次 {@code new MobEffectInstance} 分配 +
 *         效果表写入，纯属浪费（本方法的 {@link #isDurationEffectTick} 返回 true，
 *         即每 tick 都执行）。</li>
 * </ul>
 * <b>不会崩溃</b>，与 {@link MobEffectIncision} / {@link MobEffectHemorrhage} 的
 * NPE 崩溃不同——因为这里没有走到 {@code Player.die()} 那条路径。
 * </p>
 * <p>
 * <b>修复方式：</b>在方法开头加客户端守卫。失明效果自此只由服务端施加，
 * 再经原版效果同步机制下发给客户端，画面表现完全不变（客户端本就是靠同步包才知道自己失明的）。
 * </p>
 * <p>
 * <b>注意：</b>下方 {@link #onLivingUpdate}（{@code LivingTickEvent}）<b>刻意不加</b>
 * 客户端守卫——{@code setJumped} 需要在客户端也执行才能即时压制本地的跳跃预测，
 * 否则会出现「客户端跳起来又被服务端拉回去」的抖动。这与
 * {@code MobEffectGravitas} 中的同名处理保持一致。
 * </p>
 *
 * <h3>v1.2 性能：失明刷新降频 + 目标清除空判（观感完全不变）</h3>
 *
 * <h4>问题一：每 tick 施加失明 = 每 tick 一个效果同步包</h4>
 * <p>
 * v1.1 只解决了「客户端不该跑」，但服务端这一侧仍然<b>每 tick</b> 都在
 * {@code addEffect(new MobEffectInstance(BLINDNESS, 21))}。原版
 * {@code MobEffectInstance.update()} 的合并规则是「新实例的 duration 更长就覆盖并标脏」——
 * 而旧实例每 tick 递减 1（21 → 20），新实例恒为 21，<b>于是每一次都判定为更长、每一次都标脏</b>。
 * </p>
 * <p>
 * 被标脏的后果是 {@code ServerPlayer.doTick()} / {@code ServerEntity} 每 tick 向客户端
 * 下发一个 {@code ClientboundUpdateMobEffectPacket}。也就是说：
 * </p>
 * <pre>
 * 每个沉睡实体 = 20 个效果同步包 / 秒
 * </pre>
 * <p>
 * 而睡眠是<b>群体可施加</b>的——托莉娜箭（{@code EnchantmentHypnoticArrow}）与催眠烟雾
 * （{@code EnchantmentHypnoticSmoke}）在混战中可同时睡住多个目标，且每个包都要广播给
 * 全部追踪该实体的观察者。10 个沉睡目标 × 30 名观察者 = <b>每秒 6000 个包</b>，
 * 而这些包携带的信息完全相同（「你还是瞎的」）。
 * </p>
 * <p>
 * <b>修复方式：</b>改为<b>按需补充</b>——只有当前失明剩余时长不足
 * {@link #BLINDNESS_REFRESH_THRESHOLD} 时才重新施加，时长取
 * {@link #BLINDNESS_DURATION}。于是刷新周期变为
 * {@code BLINDNESS_DURATION - BLINDNESS_REFRESH_THRESHOLD = 20} tick，
 * <b>同步包数量降为原来的 1/20</b>。
 * </p>
 * <p>
 * <b>为什么不用 {@code gameTime % N} 降频：</b>那样做在「刚被施加睡眠」的瞬间，
 * 首次失明最多要等 N-1 tick 才生效，会出现将近一秒的「已经睡着但还能看见」的空窗。
 * 而按剩余时长判断天然覆盖首帧（此时 {@code getEffect} 返回 null，立刻施加），无空窗。
 * </p>
 * <p>
 * <b>行为差异（可忽略）：</b>被打醒后残留的失明由至多 21 tick 变为至多
 * {@value #BLINDNESS_DURATION} tick，即多出约 0.2 秒。考虑到觉醒本就伴随
 * ×2 以上的伤害与击退，这 0.2 秒完全不可辨；且方向上是「睡眠显得更黏一点」，
 * 与效果语义一致，不会让玩家觉得吃亏。
 * </p>
 * <p>
 * <b>顺带的好处：</b>若目标身上已有其它来源的、更长的失明（女巫、药水等），
 * 本方法不再每 tick 用一个 21 tick 的短失明去覆盖它——原实现会把长失明
 * 反复压成 21 tick，睡眠一结束对方立刻恢复视野，属于隐性的机制 bug。
 * </p>
 *
 * <h4>问题二：{@code setTarget(null)} 每 tick 空转一次全局事件分发</h4>
 * <p>
 * Forge 对 {@code Mob.setTarget} 打了补丁，<b>无论传入是否为 null 都会先触发一次
 * {@code LivingChangeTargetEvent}</b>：
 * </p>
 * <pre>
 * public void setTarget(@Nullable LivingEntity target) {
 *     LivingChangeTargetEvent e = ForgeEventFactory.onLivingChangeTarget(this, target, MOB_TARGET);
 *     if (!e.isCanceled()) { this.target = e.getNewAboutToBeSetTarget(); }
 * }
 * </pre>
 * <p>
 * 该事件是<b>全局</b>事件，一次分发要走遍事件总线上所有监听者（本模组自己就挂了两个：
 * {@link #onLivingChangeTarget} 与 {@code DynamicAttributes.StealthEventHandler}，
 * 整合包里其它模组还有更多）。而绝大多数 tick 里，沉睡实体的 target <b>本来就已经是 null</b>
 * （上一 tick 刚清过），这次分发纯属空转。
 * </p>
 * <p>
 * <b>修复方式：</b>加一个 {@code getTarget() != null} 的前置判断。
 * 只有在「AI 在本 tick 内重新锁定了目标」时才真正调用 {@code setTarget}——
 * 而这种情况本就罕见（{@link #onLivingChangeTarget} 已在 HIGHEST 优先级拦截了绝大部分锁定尝试），
 * 因此实际调用次数从每 tick 一次降到接近于零。清除目标的语义与效果完全不变。
 * </p>
 *
 * @author RoinFlam
 * @version 1.2
 */
@Mod.EventBusSubscriber
public class MobEffectSleep extends IconBase {

    /**
     * 失明效果的施加时长（tick）。
     * <p>v1.2：由 21 提高到 {@value}，配合 {@link #BLINDNESS_REFRESH_THRESHOLD} 把刷新周期
     * 拉长到 20 tick（详见类注释的「v1.2 性能」小节）。取 25 而非更大值，
     * 是为了把「被打醒后的残留失明」控制在 1.25 秒内、与原先的 1.05 秒足够接近。</p>
     */
    private static final int BLINDNESS_DURATION = 25;

    /**
     * 失明刷新阈值（tick）：当前失明剩余时长低于此值时才补一次。
     * <p>取 5 tick（0.25 秒）留出充裕余量——即便服务器出现轻微卡顿、
     * 效果 tick 有若干 tick 的延迟，失明也不会出现中断闪烁。</p>
     */
    private static final int BLINDNESS_REFRESH_THRESHOLD = 5;

    public MobEffectSleep(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.MOVEMENT_SPEED,
                "5d59080b-eda9-f5b7-1b3c-51568e5b6682",
                -1,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    /**
     * 睡眠状态下无法被设为攻击目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(@Nonnull LivingChangeTargetEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getNewTarget() == null) {
            return;
        }

        if (!(evt.getEntity() instanceof Mob)) {
            return;
        }

        Mob entityLiving = (Mob) evt.getEntity();
        if (entityLiving.hasEffect(CarianStylePotion.SLEEP.get())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 睡眠状态下无法攻击
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();
        if (attacker.hasEffect(CarianStylePotion.SLEEP.get())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 睡眠状态下受击：伤害加倍并解除睡眠
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        if (victim.hasEffect(CarianStylePotion.SLEEP.get())) {
            int amplifier = victim.getEffect(CarianStylePotion.SLEEP.get()).getAmplifier();
            // 伤害 = 原伤害×2 + 原伤害×等级×25%
            evt.setAmount(evt.getAmount() * 2 + evt.getAmount() * amplifier * 0.25f);
            victim.removeEffect(CarianStylePotion.SLEEP.get());
        }
    }

    /**
     * 睡眠状态下无法跳跃
     * <p>
     * <b>刻意不加客户端守卫：</b>跳跃压制需要在客户端同步生效，
     * 否则本地预测会让玩家先跳起来、再被服务端拉回，造成抖动。
     * </p>
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingTickEvent evt) {
        LivingEntity entityLiving = evt.getEntity();
        if (entityLiving.hasEffect(CarianStylePotion.SLEEP.get())) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    /**
     * 每 tick 清除攻击目标并按需补充失明效果。
     * <p>
     * <b>v1.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 若不加守卫，客户端会自行往本地效果表反复塞失明，与服务端下发的版本打架
     * （详见类注释的「v1.1 修复」小节）。
     * </p>
     * <p>
     * <b>v1.2 性能：</b>
     * <ul>
     *     <li>目标清除加 {@code getTarget() != null} 空判，避免每 tick 空转一次
     *         全局 {@code LivingChangeTargetEvent} 分发；</li>
     *     <li>失明改为「剩余不足 {@value #BLINDNESS_REFRESH_THRESHOLD} tick 才补」，
     *         刷新周期由 1 tick 拉长到 20 tick，效果同步包降为原来的 1/20。</li>
     * </ul>
     * 二者均不改变任何可见行为，详见类注释的「v1.2 性能」小节。
     * </p>
     *
     * @param entityLivingBaseIn 受影响的实体
     * @param amplifier          效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        // ⭐ v1.1 客户端守卫：目标清除与失明施加只在逻辑服务端执行一次，
        // 结果由原版效果同步机制下发给客户端，画面表现完全不变。
        if (entityLivingBaseIn.level().isClientSide) {
            return;
        }

        // ⭐ v1.2：仅在 AI 真的重新锁定了目标时才调用 setTarget。
        // Forge 会在 setTarget 内无条件触发一次全局 LivingChangeTargetEvent，
        // 而 target 绝大多数 tick 本就是 null（上一 tick 刚清过），空判可省掉这次空转分发。
        if (entityLivingBaseIn instanceof Mob mob && mob.getTarget() != null) {
            mob.setTarget(null);
        }

        // ⭐ v1.2：按需补充失明，而非每 tick 覆盖。
        // 首次施加时 getEffect 返回 null，立刻补上，因此不存在「刚睡着还能看见」的空窗；
        // 之后每 20 tick 才补一次，效果同步包降为原来的 1/20。
        // 若目标身上已有其它来源的、更长的失明，这里也不会再把它压短（原实现会）。
        MobEffectInstance blindness = entityLivingBaseIn.getEffect(MobEffects.BLINDNESS);
        if (blindness == null || blindness.getDuration() < BLINDNESS_REFRESH_THRESHOLD) {
            entityLivingBaseIn.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_DURATION));
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/sleep.png");
    }
}
