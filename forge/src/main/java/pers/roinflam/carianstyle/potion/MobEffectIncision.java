package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 切割药水效果
 * <p>
 * 效果：
 * - 攻击伤害+40%
 * - 攻击速度+80%
 * - 护甲-75%
 * - 韧性-75%
 * - 移动速度初始+120%，随时间衰减至+30%
 * - 治疗量+60%
 * - 每tick扣除最大生命值×12.5%/20的血量（可致死）
 * </p>
 * <p>
 * 性能优化 v3.0：
 * 移除 SynchronizationTask(1) 延迟调用，直接在 applyEffectTick 中扣血和衰减速度。
 * </p>
 *
 * <h3>v3.1 修复：客户端崩溃</h3>
 * <p>
 * <b>问题：</b>{@link #applyEffectTick} 此前缺少客户端守卫。原版
 * {@code LivingEntity.tickEffects()} 在<b>逻辑服务端与逻辑客户端两侧都会执行</b>
 * （客户端需要它来做本地预测与效果倒计时），因此本方法的全部副作用都被跑了两遍。
 * 注意<b>单人游戏同样如此</b>——单机是「一个进程内同时跑客户端与内置服务端」，
 * 玩家实体在 {@code ServerLevel} 与 {@code ClientLevel} 各有一个实例，
 * 而 {@code ClientLevel.getServer()} 恒为 null，故单机反而必然触发：
 * </p>
 * <pre>
 * MobEffectIncision.applyEffectTick
 *   -&gt; EntityLivingUtil.kill
 *     -&gt; Player.die
 *       -&gt; dropFromLootTable
 *         -&gt; level.getServer() == null  // NPE
 * </pre>
 * <p>
 * <b>修复方式：</b>在方法最开头加客户端守卫直接返回。扣血与速度衰减自此只在逻辑服务端执行一次；
 * 衰减后的修正器会由原版属性同步机制（{@code ClientboundUpdateAttributesPacket}）
 * 下发给客户端，客户端始终使用服务端的权威值。
 * </p>
 * <p>
 * <b>注意：</b>{@link #addAttributeModifiers} / {@link #removeAttributeModifiers}
 * <b>刻意不加</b>客户端守卫——客户端需要这份初始速度修正器来做移动预测，
 * 加了守卫反而会让客户端完全没有加速、造成更严重的不同步。
 * </p>
 *
 * <h3>v3.1 修复：致死路径绕过伤害管线</h3>
 * <p>
 * 致死分支此前调用 {@code EntityLivingUtil.kill}，其内部走
 * {@code damageHealthDirectly}（真伤，反射直改 {@code SynchedEntityData} 血量字段）
 * 后直接调用 {@code die()}，<b>完全跳过 {@code LivingEntity.hurt()}</b>。后果是：
 * 不触发 {@code LivingHurtEvent} / {@code LivingDamageEvent}、
 * <b>不给不死图腾任何生效机会</b>、各类免死 / 护盾类附魔全部失效——
 * 玩家会在毫无提示的情况下被自己的增益直接处决。
 * </p>
 * <p>
 * 现改为走原版 {@link LivingEntity#hurt} 管线，伤害量取最大生命的 10 倍以确保必定致命
 * （即便被护甲或减伤削减也足够）。这样死亡会正常触发全套事件与图腾判定，
 * 战利品掉落也只会在服务端发生。
 * </p>
 * <p>
 * <b>逐 tick 的持续扣血仍保留 {@code setHealth} 直扣</b>，未改为 {@code hurt}——
 * 每秒 20 次走完整伤害管线会触发全部受击类附魔（因果律累积、各类反伤等）各 20 次，
 * 既是性能灾难也会让机制行为完全跑偏。持续掉血本就是「自我消耗」而非「受到攻击」，
 * 直扣血量是正确的语义。
 * </p>
 *
 * <h3>v3.2 更正：视角抖动的真实成因（并非属性漂移）</h3>
 * <p>
 * v3.1 的类注释曾把「视角持续抖动」归因于双端各自独立衰减移速导致数值漂移、
 * 进而触发服务端位置校正（橡皮筋）。<b>该诊断是错的</b>，特此更正——
 * 玩家移动在原版中是客户端权威、服务端仅做超速兜底校验（阈值约 100 blocks²/tick），
 * +120% 移速远达不到该阈值，不会产生任何位置回拉。
 * </p>
 * <p>
 * <b>真实成因是每 tick 掉血触发了原版的「受伤镜头倾斜」：</b>
 * </p>
 * <ol>
 *     <li>本效果的 {@link #isDurationEffectTick} 恒为 true，故每 tick 都会
 *         {@code setHealth}，服务端 {@code ServerPlayer.doTick()} 随之每 tick
 *         下发一个 {@code ClientboundSetHealthPacket}；</li>
 *     <li>客户端 {@code ClientPacketListener.handleSetHealth} 调用
 *         {@code Player.hurtTo}，后者在生命值下降时把 {@code hurtTime} 重置为 10；</li>
 *     <li>{@code GameRenderer.bobHurt()} 依据 {@code hurtTime} 对摄像机做最大 14° 的
 *         Z 轴 roll。正常受击时该值递减到 0（约 0.5 秒）晃一下就停，
 *         但切腹期间它每 tick 被顶回 10，曲线永远从头播 —— 于是持续抽搐。</li>
 * </ol>
 * <p>
 * 该问题已由客户端 Mixin {@code MixinPlayerHurtTo} 修复（拦截 {@code hurtTo}，
 * 处于本效果期间只做纯 {@code setHealth}、跳过受伤动画字段的赋值）。
 * <b>本类的机制逻辑无需为此改动。</b>
 * </p>
 *
 * <h3>v3.2 性能：移速衰减降频（机制与观感等价）</h3>
 * <p>
 * 移速衰减此前<b>每 tick</b> 执行一次 {@code removeModifier + addTransientModifier}。
 * 该操作会把 {@code MOVEMENT_SPEED} 标记为脏属性，导致
 * {@code ServerEntity.sendDirtyEntityData()} <b>每 tick</b> 额外下发一个
 * {@code ClientboundUpdateAttributesPacket}——10 秒切腹即 200 个多余的属性同步包，
 * 多人服中若同时有数人处于切腹状态则成倍放大。
 * </p>
 * <p>
 * 现改为每 {@link #SPEED_DECAY_INTERVAL_TICKS} tick 衰减一次、单步衰减量等比放大
 * （{@link #SPEED_DECAY_PER_STEP}），<b>每秒的总衰减速率与优化前完全一致</b>：
 * 仍是 1.20（+120%）在 6 秒内线性降到 0.30（+30%）。
 * 单次衰减量 0.0375 折算到移速上不足 4 个百分点，且原版属性同步本就有插值缺失，
 * 玩家无法察觉「连续衰减」与「每 0.25 秒一档」的区别。
 * 同步包数量由 200 降至 40。
 * </p>
 * <p>
 * <b>调参说明：</b>若希望衰减手感更平滑，把 {@link #SPEED_DECAY_INTERVAL_TICKS} 调小即可
 * （同时按比例调小 {@link #SPEED_DECAY_PER_STEP}，二者乘积须恒为每 tick 0.0075，
 * 否则会改变「6 秒降到底」这一机制时长）。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@Mod.EventBusSubscriber
public class MobEffectIncision extends IconBase {

    /** 移动速度修改器UUID */
    public static final UUID ID = UUID.fromString("0a6b62ca-ead9-3641-c4dd-a4d33daf5cc1");

    /** 移动速度修改器名称 */
    public static final String NAME = "potion.incision";

    /** 速度衰减下限 */
    private static final double SPEED_DECAY_FLOOR = 0.3;

    /**
     * 速度衰减的执行间隔（tick）。
     * <p>v3.2：由每 tick 改为每 5 tick 执行一次，以削减 {@code MOVEMENT_SPEED}
     * 的脏属性同步包（详见类注释「v3.2 性能」小节）。</p>
     */
    private static final int SPEED_DECAY_INTERVAL_TICKS = 5;

    /**
     * 单次衰减量（每 {@link #SPEED_DECAY_INTERVAL_TICKS} tick 扣减的加成值）。
     * <p>{@code 0.15 / 4.0 = 0.0375}，即每秒衰减 0.15 —— 与 v3.1 的「每 tick 衰减
     * {@code 0.15/20}」<b>速率完全相同</b>，机制时长不变（1.20 → 0.30 耗时 6 秒）。</p>
     */
    private static final double SPEED_DECAY_PER_STEP = 0.15 / 4.0;

    /**
     * 致死伤害倍率（× 最大生命）。
     * <p>取足够大的值确保穿透护甲与各类减伤后仍然致命，同时避免使用
     * {@code Float.MAX_VALUE} 在后续伤害计算中产生溢出 / NaN。</p>
     */
    private static final float LETHAL_DAMAGE_MULTIPLIER = 10.0f;

    public MobEffectIncision(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                "0788fd21-aade-d9dc-0daa-faee0f26e5ee",
                0.4,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                "07a1b38c-e245-d4c0-1e0e-6529582fbb6d",
                0.8,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR,
                "53c9ebac-b292-2d82-993a-cb183e208411",
                -0.75,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "68d0f463-1a46-6e25-2ed1-c0aec31b641e",
                -0.75,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void removeAttributeModifiers(@Nonnull LivingEntity entityLivingBaseIn,
                                         @Nonnull AttributeMap attributeMapIn,
                                         int amplifier) {
        AttributeInstance speedAttr = entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(ID);
        }
        super.removeAttributeModifiers(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    @Override
    public void addAttributeModifiers(@Nonnull LivingEntity entityLivingBaseIn,
                                      @Nonnull AttributeMap attributeMapIn,
                                      int amplifier) {
        // 初始移动速度+120%
        // 注意：此处刻意不加客户端守卫——客户端需要这份修正器做移动预测，
        // 若客户端没有加速会与服务端产生更严重的不同步（详见类注释）
        AttributeInstance speedAttr = entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.addTransientModifier(
                    new AttributeModifier(ID, NAME, 1.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        super.addAttributeModifiers(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    /**
     * 治疗量+60%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity healer = evt.getEntity();
        if (healer.hasEffect(CarianStylePotion.INCISION.get())) {
            evt.setAmount(evt.getAmount() * 1.6f);
        }
    }

    /**
     * 每tick扣血 + 速度衰减（直接执行，不再创建延迟任务）
     * <p>
     * <b>v3.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 若不加守卫，客户端会自行走死亡逻辑并因 {@code getServer()} 为 null 而崩溃
     * （详见类注释的「v3.1 修复」小节）。
     * </p>
     * <p>
     * <b>v3.2：速度衰减改为每 {@link #SPEED_DECAY_INTERVAL_TICKS} tick 执行一次</b>，
     * 每秒总衰减速率不变，仅削减属性同步包数量（详见类注释的「v3.2 性能」小节）。
     * 扣血仍保持每 tick，以维持原有的掉血手感与致死时机。
     * </p>
     *
     * @param entityLivingBaseIn 受影响的实体
     * @param amplifier          效果等级
     */
    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        // ⭐ v3.1 客户端守卫：扣血与速度衰减只在逻辑服务端执行一次。
        // 缺少此判断会导致客户端自行走死亡逻辑 -> getServer() 为 null -> 崩溃。
        if (entityLivingBaseIn.level().isClientSide) {
            return;
        }

        // ========== 扣血 ==========
        float damage = entityLivingBaseIn.getMaxHealth() * 0.125f / 20;

        if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
            // 常规掉血：直扣血量，不走伤害管线。
            // 每秒 20 次走 hurt() 会让全部受击类附魔各触发 20 次，
            // 既是性能灾难也会让机制行为跑偏；持续掉血语义上是「自我消耗」而非「受到攻击」。
            entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
        } else {
            // ⭐ v3.1 致死路径：改走原版伤害管线，不再用 EntityLivingUtil.kill 强制处决。
            // 原实现会绕过 hurt()，导致不死图腾与各类免死机制全部失效。
            entityLivingBaseIn.hurt(
                    NewDamageSource.hemorrhage(entityLivingBaseIn.level()),
                    entityLivingBaseIn.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER
            );
            return;
        }

        // ========== 速度衰减 ==========
        // ⭐ v3.2 降频：每 SPEED_DECAY_INTERVAL_TICKS tick 才衰减一次。
        // 用世界游戏刻取模而非各实体独立计数，无需额外状态；不同实体的衰减时机同相，
        // 但衰减是纯数值变化、无视觉节奏可言，同相不会产生任何观感问题。
        if (entityLivingBaseIn.level().getGameTime() % SPEED_DECAY_INTERVAL_TICKS != 0) {
            return;
        }

        AttributeInstance attributeInstance = entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeInstance == null) {
            return;
        }

        AttributeModifier modifier = attributeInstance.getModifier(ID);
        if (modifier != null) {
            double currentBonus = modifier.getAmount();
            if (currentBonus > SPEED_DECAY_FLOOR) {
                attributeInstance.removeModifier(ID);
                double newBonus = Math.max(currentBonus - SPEED_DECAY_PER_STEP, SPEED_DECAY_FLOOR);
                attributeInstance.addTransientModifier(
                        new AttributeModifier(ID, NAME, newBonus, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
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
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/incision.png");
    }
}
