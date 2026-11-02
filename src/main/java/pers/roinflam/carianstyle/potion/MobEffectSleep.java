package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import javax.annotation.Nonnull;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.event.LivingTickEvent;

/**
 * PORT-STUB(B2a)
 *
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
@Deprecated
public class MobEffectSleep extends IconBase {
    private static final int BLINDNESS_DURATION = 25;
    private static final int BLINDNESS_REFRESH_THRESHOLD = 5;
    public MobEffectSleep(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return false;
    }
    @Nonnull @OnlyIn(Dist.CLIENT) protected ResourceLocation getIconTexture() {
        return null;
    }
}

