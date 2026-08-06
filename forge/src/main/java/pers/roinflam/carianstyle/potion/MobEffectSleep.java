package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
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
 * @author RoinFlam
 * @version 1.1
 */
@Mod.EventBusSubscriber
public class MobEffectSleep extends IconBase {

    /**
     * 失明效果的施加时长（tick）。
     * <p>取 21 而非 20，是为了留出 1 tick 余量——本方法每 tick 刷新一次，
     * 21 tick 的时长确保在下次刷新到达前不会出现失明短暂中断的闪烁。</p>
     */
    private static final int BLINDNESS_DURATION = 21;

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
     * 每 tick 清除攻击目标并刷新失明效果。
     * <p>
     * <b>v1.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 若不加守卫，客户端会自行往本地效果表反复塞失明，与服务端下发的版本打架
     * （详见类注释的「v1.1 修复」小节）。
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

        // 清除生物的攻击目标
        if (entityLivingBaseIn instanceof Mob) {
            ((Mob) entityLivingBaseIn).setTarget(null);
        }
        // 施加失明效果
        entityLivingBaseIn.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_DURATION));
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
