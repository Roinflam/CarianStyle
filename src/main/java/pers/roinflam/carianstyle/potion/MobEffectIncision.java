package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.mc112.attr.Operation;
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
 * <b>问题：</b>{@link #applyEffectTick} 此前缺少客户端守卫。原版的效果 tick
 * 在<b>逻辑服务端与逻辑客户端两侧都会执行</b>（客户端需要它来做效果倒计时），
 * 因此本方法的全部副作用都被跑了两遍。注意<b>单人游戏同样如此</b>——单机是
 * 「一个进程内同时跑客户端与内置服务端」，玩家实体在两边各有一个实例，
 * 客户端那份自己走致死逻辑，会在只有服务端才有的掉落 / 死亡流程里出错。
 * </p>
 * <p>
 * <b>修复方式：</b>在方法最开头加客户端守卫直接返回。扣血与速度衰减自此只在逻辑服务端执行一次；
 * 衰减后的修正器会由原版属性同步机制下发给客户端，客户端始终使用服务端的权威值。
 * </p>
 * <p>
 * <b>注意：</b>{@link #applyAttributesModifiersToEntity} / {@link #removeAttributesModifiersFromEntity}
 * <b>不加</b>客户端守卫。1.12.2 的原版只在服务端调这两个方法，速度修正器经属性同步包下发给客户端做移动预测。
 * </p>
 *
 * <h3>v3.1 修复：致死路径绕过伤害管线</h3>
 * <p>
 * 致死分支此前调用 {@code EntityLivingUtil.kill}，直改血量后直接走死亡，<b>完全跳过伤害管线</b>。
 * 后果是：不触发 {@code LivingHurtEvent} / {@code LivingDamageEvent}、
 * <b>不给不死图腾任何生效机会</b>、各类免死 / 护盾类附魔全部失效——
 * 玩家会在毫无提示的情况下被自己的增益直接处决。
 * </p>
 * <p>
 * 现改为走原版伤害管线（1.12.2 是 {@code attackEntityFrom}），伤害量取最大生命的 10 倍以确保必定致命
 * （即便被护甲或减伤削减也足够；出血伤害源是绝对伤害，抗性提升 V 也挡不住）。
 * 这样死亡会正常触发全套事件与图腾判定，战利品掉落也只会在服务端发生。
 * </p>
 * <p>
 * <b>逐 tick 的持续扣血仍保留 {@code setHealth} 直扣</b>，未改为走伤害——
 * 每秒 20 次走完整伤害管线会触发全部受击类附魔（因果律累积、各类反伤等）各 20 次，
 * 既是性能灾难也会让机制行为完全跑偏。持续掉血本就是「自我消耗」而非「受到攻击」，
 * 直扣血量是正确的语义。
 * </p>
 *
 * <h3>v3.2 更正：视角抖动的真实成因（并非属性漂移）</h3>
 * <p>
 * 视角持续抖动不是属性漂移造成的，而是每 tick 掉血触发了原版的「受伤镜头倾斜」：
 * 本效果每 tick 都会 {@code setHealth}，服务端每 tick 下发一次血量包，客户端收到后
 * 生命值下降就把 {@code hurtTime} 重置为 10，镜头按它做最大 14° 的 roll；
 * 切腹期间它每 tick 被顶回 10，曲线永远从头播 —— 于是持续抽搐。
 * </p>
 * <p>
 * 1.20.1 由客户端 Mixin 修；1.12.2 版由客户端的 {@code mc112.hook.HealthPacketHook} 处理
 * （处于本效果期间只设血量、不动受伤动画字段）。<b>本类的机制逻辑无需为此改动。</b>
 * </p>
 *
 * <h3>v3.2 性能：移速衰减降频（机制与观感等价）</h3>
 * <p>
 * 移速衰减此前<b>每 tick</b> 执行一次「删修正器 + 加修正器」。
 * 该操作会把 {@code MOVEMENT_SPEED} 标记为脏属性，导致服务端<b>每 tick</b> 额外下发一个属性同步包
 * ——10 秒切腹即 200 个多余的属性同步包，多人服中若同时有数人处于切腹状态则成倍放大。
 * </p>
 * <p>
 * 现改为每 {@link #SPEED_DECAY_INTERVAL_TICKS} tick 衰减一次、单步衰减量等比放大
 * （{@link #SPEED_DECAY_PER_STEP}），<b>每秒的总衰减速率与优化前完全一致</b>：
 * 仍是 1.20（+120%）在 6 秒内线性降到 0.30（+30%）。
 * 单次衰减量 0.0375 折算到移速上不足 4 个百分点，玩家无法察觉「连续衰减」与「每 0.25 秒一档」的区别。
 * 同步包数量由 200 降至 40。
 * </p>
 * <p>
 * <b>调参说明：</b>若希望衰减手感更平滑，把 {@link #SPEED_DECAY_INTERVAL_TICKS} 调小即可
 * （同时按比例调小 {@link #SPEED_DECAY_PER_STEP}，二者乘积须恒为每 tick 0.0075，
 * 否则会改变「6 秒降到底」这一机制时长）。
 * </p>
 * <p>
 * 1.12.2 版：速度修正器 {@code setSaved(false)}，对应 1.20.1 的 {@code addTransientModifier}，不写进存档。
 * </p>
 *
 * @author RoinFlam
 * @version 3.2
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
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
        super(isBadEffectIn, liquidColorIn);
        // 1.12.2 版：registerPotionAttributeModifier 拿药水名当修饰符名、名字为空会抛异常，
        // 注册器要到构造之后才设名字，这里先设上同一个键
        this.setPotionName("effect." + Reference.MOD_ID + ".incision");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_DAMAGE,
                "0788fd21-aade-d9dc-0daa-faee0f26e5ee",
                0.4,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_SPEED,
                "07a1b38c-e245-d4c0-1e0e-6529582fbb6d",
                0.8,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ARMOR,
                "53c9ebac-b292-2d82-993a-cb183e208411",
                -0.75,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ARMOR_TOUGHNESS,
                "68d0f463-1a46-6e25-2ed1-c0aec31b641e",
                -0.75,
                Operation.MULTIPLY_TOTAL.id()
        );
    }

    /**
     * 新建一份速度修正器（不存档）
     *
     * @param amount 加成
     * @return 修正器
     */
    @Nonnull
    private static AttributeModifier speedModifier(double amount) {
        return new AttributeModifier(ID, NAME, amount, Operation.MULTIPLY_TOTAL.id()).setSaved(false);
    }

    @Override
    public void removeAttributesModifiersFromEntity(@Nonnull EntityLivingBase entityLivingBaseIn,
                                                    @Nonnull AbstractAttributeMap attributeMapIn,
                                                    int amplifier) {
        IAttributeInstance speedAttr = entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.removeModifier(ID);
        }
        super.removeAttributesModifiersFromEntity(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    @Override
    public void applyAttributesModifiersToEntity(@Nonnull EntityLivingBase entityLivingBaseIn,
                                                 @Nonnull AbstractAttributeMap attributeMapIn,
                                                 int amplifier) {
        // 初始移动速度+120%
        IAttributeInstance speedAttr = entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            // 1.12.2 的 applyModifier 遇到同 UUID 已存在会抛异常（重复施加时原版会先 remove 再 apply，这里保险起见先删）
            speedAttr.removeModifier(ID);
            speedAttr.applyModifier(speedModifier(1.2));
        }
        super.applyAttributesModifiersToEntity(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    /**
     * 治疗量+60%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase healer = evt.getEntityLiving();
        if (healer.isPotionActive(CarianStylePotion.INCISION.get())) {
            evt.setAmount(evt.getAmount() * 1.6f);
        }
    }

    /**
     * 每tick扣血 + 速度衰减（直接执行，不再创建延迟任务）
     * <p>
     * <b>v3.1：仅在逻辑服务端执行。</b>原版会在双端各调用一次本方法，
     * 若不加守卫，客户端会自行走死亡逻辑（详见类注释的「v3.1 修复」小节）。
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
    public void applyEffectTick(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        // v3.1 客户端守卫：扣血与速度衰减只在逻辑服务端执行一次。
        if (entityLivingBaseIn.world.isRemote) {
            return;
        }

        // ========== 扣血 ==========
        // 回溯无敌期间不掉血：直扣血量拦不到，致死那一击的 10 倍最大生命还会被算进储存伤害
        if (!EnchantmentTimeReversal.isReversing(entityLivingBaseIn)) {
            float damage = entityLivingBaseIn.getMaxHealth() * 0.125f / 20;

            if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
                // 常规掉血：直扣血量，不走伤害管线。
                // 每秒 20 次走伤害会让全部受击类附魔各触发 20 次，
                // 既是性能灾难也会让机制行为跑偏；持续掉血语义上是「自我消耗」而非「受到攻击」。
                entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
            } else {
                // v3.1 致死路径：改走原版伤害管线，不再用 EntityLivingUtil.kill 强制处决。
                // 原实现会绕过伤害管线，导致不死图腾与各类免死机制全部失效。
                entityLivingBaseIn.attackEntityFrom(
                        NewDamageSource.hemorrhage(entityLivingBaseIn.world),
                        entityLivingBaseIn.getMaxHealth() * LETHAL_DAMAGE_MULTIPLIER
                );
                return;
            }
        }

        // ========== 速度衰减 ==========
        // v3.2 降频：每 SPEED_DECAY_INTERVAL_TICKS tick 才衰减一次。
        // 用世界游戏刻取模而非各实体独立计数，无需额外状态；不同实体的衰减时机同相，
        // 但衰减是纯数值变化、无视觉节奏可言，同相不会产生任何观感问题。
        if (entityLivingBaseIn.world.getTotalWorldTime() % SPEED_DECAY_INTERVAL_TICKS != 0) {
            return;
        }

        IAttributeInstance attributeInstance = entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attributeInstance == null) {
            return;
        }

        AttributeModifier modifier = attributeInstance.getModifier(ID);
        if (modifier != null) {
            double currentBonus = modifier.getAmount();
            if (currentBonus > SPEED_DECAY_FLOOR) {
                attributeInstance.removeModifier(ID);
                double newBonus = Math.max(currentBonus - SPEED_DECAY_PER_STEP, SPEED_DECAY_FLOOR);
                attributeInstance.applyModifier(speedModifier(newBonus));
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/incision.png");
    }
}
