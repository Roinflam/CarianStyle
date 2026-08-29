package pers.roinflam.carianstyle.dynamicattr.dynamiceffect;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncAttribute;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttribute;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.HowlShabririSyncHelper;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import java.util.List;
import java.util.UUID;

/**
 * 卡利亚风格模组 - 动态属性注册
 * 完全独立的效果系统，不依赖任何药水
 *
 * <h3>性能优化 v2.1（效果行为基本不变，差异见下）</h3>
 * <p>
 * <b>问题：per-entity 事件处理器挂在全局事件总线上，却监听高频事件。</b>
 * {@code DynamicAttributeInstance.setEventHandler} 会把每个实体的处理器
 * 单独 {@code MinecraftForge.EVENT_BUS.register}。若处理器里监听的是
 * {@code LivingEvent.LivingTickEvent}，就意味着：
 * </p>
 * <pre>
 * 回调次数 = 持有该效果的实体数 × 区块内全部活体数 × 每 tick
 * </pre>
 * <p>
 * 50 个实体带岩石剑、区块内 1000 个活体 → 每 tick 5 万次回调，
 * 而其中 99.9% 只是做一次 UUID 比对就返回。原先的岩石剑（跳跃压制）与
 * 隐身（清仇恨）都属于这种情况。
 * </p>
 * <p>
 * <b>做法：</b>改用本框架<b>自带的</b> {@link DynamicAttribute#onTick} 回调机制。
 * 该回调由 {@code DynamicAttributeManager.processEntityTick} 驱动，
 * 只会对<b>真正持有该效果的实体</b>执行，且入口已有
 * {@code ENTITY_ATTRIBUTES.isEmpty()} 与 {@code containsKey()} 双重快速退出。
 * 两个 {@code LivingTickEvent} 监听器因此被完全移除。
 * </p>
 * <ul>
 *     <li><b>岩石剑：</b>{@code CragbladeEventHandler} 整个删除，改为
 *         {@code onTick(setJumped) + setTickInterval(1)}。原处理器本就没有客户端守卫，
 *         但它调用的 {@code DynamicAttributeManager.has} 在客户端恒为 false
 *         （效果数据只存在于服务端），因此实际只在服务端生效——与 onTick 完全一致，
 *         跳跃压制的手感一模一样。</li>
 *     <li><b>隐身：</b>处理器保留（仍需监听 {@code LivingChangeTargetEvent} 与
 *         客户端渲染事件），但其中的 {@code LivingTickEvent} 被移除，
 *         清仇恨改为 {@code onTick + setTickInterval(}{@value #STEALTH_AGGRO_CLEAR_INTERVAL}{@code )}。</li>
 * </ul>
 *
 * <h3>关于隐身清仇恨频率的行为差异（唯一一处，已做补偿）</h3>
 * <p>
 * 优化前，{@code EnchantmentConcealingVeil} 潜行时每 tick 施加一次隐身，
 * 而旧版 {@code apply} 会整体重建实例 → 事件处理器被重建 → {@code hasInitialized}
 * 复位 → <b>每约 2 tick 就执行一次 32 格范围的清仇恨查询</b>。
 * 这既是重复劳动，也伴随着客户端同步包的反复收发（见
 * {@code DynamicAttributeManager} v2.2 的说明）。
 * </p>
 * <p>
 * 优化后改为固定每 {@value #STEALTH_AGGRO_CLEAR_INTERVAL} tick（0.5 秒）清一次，
 * 频率降为约 1/10。<b>为什么不影响体验：</b>隐身效果本身在
 * {@code LivingChangeTargetEvent} 上以 HIGHEST 优先级拦截了所有「把隐身者设为目标」的尝试，
 * 因此进入隐身后<b>没有任何生物能重新锁定你</b>；清仇恨只负责处理「进入隐身前就已锁定你」
 * 的那批生物，清一次即可，0.5 秒的周期复查纯属冗余保险。
 * </p>
 *
 * <h3>v2.2 新增：嘶吼层数的客户端同步（本次改动，机制零影响）</h3>
 * <p>
 * {@link #HOWL_SHABRIRI} 此前只有属性修正器与治疗削减，<b>客户端完全不知道它的存在</b>——
 * 因为 {@code DynamicAttributeManager} 的数据只在服务端产生，客户端那份 Map 恒为空。
 * 于是「目标身上叠了几层发狂」这个信息对玩家完全不可见，
 * 而该附魔的核心机制恰恰是「对满层目标额外 +15%×等级 伤害」，
 * 玩家必须能看出层数才知道下一击是否吃到加成。
 * </p>
 * <p>
 * 本次在静态块里给它挂上 {@code onApplied} / {@code onRemoved} 两个生命周期回调，
 * 转交 {@link HowlShabririSyncHelper} 把层数同步到客户端（每层一个序列号，
 * 序列号 13~18，详见该类注释）。
 * </p>
 * <p>
 * <b>为什么改在这里而不是改附魔类：</b>叠层、覆盖、自然过期、实体死亡（{@code clearAll}）
 * 全部路径都会经过这两个回调，挂在属性定义上是唯一不会漏网的位置；
 * 而 {@code EnchantmentHowlShabriri} 里只有「施加」这一处，
 * 在那里同步就无法覆盖过期与死亡，会造成层数视觉残留。
 * <b>该附魔因此一行都不用改。</b>
 * </p>
 * <p>
 * <b>机制影响为零：</b>生命周期回调只做网络广播，不碰任何属性修正器、不改 amplifier、
 * 不影响 {@code DynamicAttributeManager} 的覆盖判定；
 * 而 v2.2 的「同等级重复 apply 只刷新时长、直接返回」这一早退分支
 * 意味着攻击同一目标但层数已封顶时<b>连回调都不会触发</b>，不会产生任何多余的包。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
public class DynamicAttributes {

    /**
     * 隐身状态下清除周边仇恨的间隔（tick）。
     * <p>10 tick = 0.5 秒。取值理由见类注释「关于隐身清仇恨频率的行为差异」。
     * 调小更灵敏但更耗，调大更省；由于目标锁定本身已被事件拦截，该值不影响实际隐身效果。</p>
     */
    private static final int STEALTH_AGGRO_CLEAR_INTERVAL = 10;

    /** 隐身状态下清除仇恨的搜索半径（格） */
    private static final double STEALTH_CLEAR_AGGRO_RANGE = 32.0;

    // ========== 基础增益效果 ==========

    /**
     * 攻击提升效果
     * - 攻击伤害+1%×等级
     * - 攻击速度+2%×等级
     */
    public static final DynamicAttribute ATTACK_BOOST = new DynamicAttribute("carianstyle_attack_boost")
            .addModifier(Attributes.ATTACK_DAMAGE, 0.01, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ATTACK_SPEED, 0.02, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /**
     * 速度提升效果
     * - 移动速度+1%×等级
     */
    public static final DynamicAttribute SPEED_BOOST = new DynamicAttribute("carianstyle_speed_boost")
            .addModifier(Attributes.MOVEMENT_SPEED, 0.01, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ========== 防御效果 ==========

    /**
     * 龙徽大盾效果
     * - 护甲+7.5%×等级
     * - 韧性+7.5%×等级
     * - 移动速度-1%×等级
     * - 飞行速度-1%×等级
     */
    public static final DynamicAttribute DRAGONCREST_GREATSHIELD = new DynamicAttribute("carianstyle_dragoncrest_greatshield")
            .addModifier(Attributes.ARMOR, 0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, 0.075, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.MOVEMENT_SPEED, -0.01, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.FLYING_SPEED, -0.01, AttributeModifier.Operation.MULTIPLY_TOTAL);

    // ========== 特殊武器效果 ==========

    /**
     * 岩石剑效果
     * - 攻击力+10%×等级
     * - 击退抗性+10%×等级
     * - 护甲+10%×等级
     * - 韧性+10%×等级
     * - 无法跳跃
     * <p>
     * v2.1：跳跃压制由原来的 per-entity {@code LivingTickEvent} 处理器
     * 改为框架自带的 onTick 回调（每 tick 触发），只对持有该效果的实体执行。
     * </p>
     */
    public static final DynamicAttribute CRAGBLADE = new DynamicAttribute("carianstyle_cragblade")
            .addModifier(Attributes.ATTACK_DAMAGE, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.KNOCKBACK_RESISTANCE, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .onTick(context -> EntityLivingUtil.setJumped(context.getEntity()))
            .setTickInterval(1);

    // ========== 负面效果 ==========

    /**
     * 注定死亡效果
     * - 最大生命值-25%×等级
     */
    public static final DynamicAttribute DOOMED_DEATH = new DynamicAttribute("carianstyle_doomed_death")
            .addModifier(Attributes.MAX_HEALTH, -0.25, AttributeModifier.Operation.MULTIPLY_TOTAL);

    /**
     * 沙布里里的嚎叫效果
     * - 护甲-15%×等级
     * - 韧性-15%×等级
     * - 治疗量-10%×(等级+1)
     * <p>
     * v2.2：新增层数的客户端同步（见静态块），使观察者能看出目标叠了几层发狂。
     * 同步只做网络广播，不影响本效果的任何数值与判定。
     * </p>
     */
    public static final DynamicAttribute HOWL_SHABRIRI = new DynamicAttribute("carianstyle_howl_shabriri")
            .addModifier(Attributes.ARMOR, -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .withEventHandler(HowlShabririEventHandler::new);

    // ========== 隐身效果 ==========

    /**
     * 隐身效果
     * - 玩家模型不渲染（隐形）
     * - 生物无法将此实体设为攻击目标
     * - 周期性清除周围32格所有生物的仇恨
     * - 客户端同步渲染：序列号4
     * <p>
     * v2.1：清仇恨由原来的 per-entity {@code LivingTickEvent} 处理器
     * 改为框架自带的 onTick 回调（每 {@value #STEALTH_AGGRO_CLEAR_INTERVAL} tick 触发）。
     * 事件处理器保留，仍负责拦截目标锁定与客户端渲染。
     * </p>
     */
    public static final DynamicAttribute STEALTH = new DynamicAttribute("carianstyle_stealth")
            .onTick(context -> clearNearbyAggroTowards(context.getEntity()))
            .setTickInterval(STEALTH_AGGRO_CLEAR_INTERVAL)
            .withEventHandler(StealthEventHandler::new);

    // ========== 火焰燃烧效果 ==========

    /**
     * 注定死亡燃烧效果
     * - 火焰渲染：猩红色火焰（序列号1）
     */
    public static final DynamicAttribute DOOMED_DEATH_BURNING = new DynamicAttribute("carianstyle_doomed_death_burning");

    /**
     * 毁灭火焰燃烧效果
     * - 火焰渲染：白色火焰（序列号2）
     */
    public static final DynamicAttribute DESTRUCTION_FIRE_BURNING = new DynamicAttribute("carianstyle_destruction_fire_burning");

    /**
     * 癫痫火焰燃烧效果
     * - 治疗量减少90%
     * - 火焰渲染：黄色火焰（序列号3）
     */
    public static final DynamicAttribute EPILEPSY_FIRE_BURNING = new DynamicAttribute("carianstyle_epilepsy_fire_burning")
            .withEventHandler(EpilepsyFireEventHandler::new);

    // ========== 静态初始化：注册需要客户端同步的属性 ==========

    static {
        // 注册火焰属性与序列号的映射
        ClientSyncAttribute.register(DOOMED_DEATH_BURNING, 1);
        ClientSyncAttribute.register(DESTRUCTION_FIRE_BURNING, 2);
        ClientSyncAttribute.register(EPILEPSY_FIRE_BURNING, 3);

        // 注册隐身效果与序列号的映射
        ClientSyncAttribute.register(STEALTH, 4);

        // 为火焰属性添加生命周期回调
        DOOMED_DEATH_BURNING
                .onApplied(ClientSyncEffectHelper::onAttributeApplied)
                .onRemoved(ClientSyncEffectHelper::onAttributeRemoved);

        DESTRUCTION_FIRE_BURNING
                .onApplied(ClientSyncEffectHelper::onAttributeApplied)
                .onRemoved(ClientSyncEffectHelper::onAttributeRemoved);

        EPILEPSY_FIRE_BURNING
                .onApplied(ClientSyncEffectHelper::onAttributeApplied)
                .onRemoved(ClientSyncEffectHelper::onAttributeRemoved);

        // 为隐身效果添加生命周期回调
        STEALTH
                .onApplied(ClientSyncEffectHelper::onAttributeApplied)
                .onRemoved(ClientSyncEffectHelper::onAttributeRemoved);

        // ⭐ v2.2：嘶吼的层数同步。
        // 刻意不走 ClientSyncAttribute + ClientSyncEffectHelper 那套——
        // 那套是「一个属性对应一个固定序列号」，只能表达「有 / 没有」，
        // 而嘶吼需要表达「第几层」，故用 HowlShabririSyncHelper 的
        // 「一层一个序列号」方案（序列号 13~18，详见该类注释）。
        HOWL_SHABRIRI
                .onApplied(HowlShabririSyncHelper::onApplied)
                .onRemoved(HowlShabririSyncHelper::onRemoved);
    }

    // ========== 共享工具方法 ==========

    /**
     * 清除周围生物对指定实体的仇恨。
     * <p>
     * v2.1：由 {@code StealthEventHandler} 的实例方法提为静态方法，
     * 供 {@link #STEALTH} 的 onTick 回调直接调用。逻辑与原实现一字未改。
     * </p>
     *
     * @param target 需要被“遗忘”的目标实体
     */
    private static void clearNearbyAggroTowards(LivingEntity target) {
        if (target.level().isClientSide()) {
            return;
        }

        AABB searchBox = new AABB(
                target.getX() - STEALTH_CLEAR_AGGRO_RANGE,
                target.getY() - STEALTH_CLEAR_AGGRO_RANGE,
                target.getZ() - STEALTH_CLEAR_AGGRO_RANGE,
                target.getX() + STEALTH_CLEAR_AGGRO_RANGE,
                target.getY() + STEALTH_CLEAR_AGGRO_RANGE,
                target.getZ() + STEALTH_CLEAR_AGGRO_RANGE
        );

        List<Mob> nearbyMobs = target.level().getEntitiesOfClass(
                Mob.class,
                searchBox,
                mob -> mob.getTarget() != null && mob.getTarget().equals(target)
        );

        for (Mob mob : nearbyMobs) {
            mob.setTarget(null);
        }
    }

    // ========== 命名内部类：事件处理器 ==========

    /**
     * 沙布里里的嚎叫效果的事件处理器
     * 减少治疗量
     * <p>只监听低频的 {@code LivingHealEvent}，无需改造。</p>
     */
    private static class HowlShabririEventHandler {
        private final UUID boundEntityId;

        public HowlShabririEventHandler(LivingEntity entity) {
            this.boundEntityId = entity.getUUID();
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onHeal(LivingHealEvent event) {
            if (!event.getEntity().getUUID().equals(boundEntityId)) return;
            if (event.getEntity().level().isClientSide()) return;

            int level = DynamicAttributeManager.getAmplifier((LivingEntity) event.getEntity(),
                    DynamicAttributes.HOWL_SHABRIRI);
            if (level < 0) return;

            float reduction = (level + 1) * 0.1f;
            event.setAmount(event.getAmount() * (1 - reduction));
        }
    }

    /**
     * 癫痫火焰燃烧效果的事件处理器
     * 大幅减少治疗量(90%)
     * <p>只监听低频的 {@code LivingHealEvent}，无需改造。</p>
     */
    private static class EpilepsyFireEventHandler {
        private final UUID boundEntityId;

        public EpilepsyFireEventHandler(LivingEntity entity) {
            this.boundEntityId = entity.getUUID();
        }

        @SubscribeEvent(priority = EventPriority.LOW)
        public void onHeal(LivingHealEvent event) {
            if (!event.getEntity().getUUID().equals(boundEntityId)) return;
            if (event.getEntity().level().isClientSide()) return;

            if (DynamicAttributeManager.has((LivingEntity) event.getEntity(),
                    DynamicAttributes.EPILEPSY_FIRE_BURNING)) {
                event.setAmount(event.getAmount() * 0.1f);
            }
        }
    }

    /**
     * 隐身效果的事件处理器
     * <p>
     * v2.1：原先的 {@code LivingTickEvent} 监听（首次 tick 清仇恨）已移除，
     * 改由 {@link #STEALTH} 的 onTick 回调周期执行——详见类注释。
     * 本处理器现仅保留两项：拦截生物锁定目标、客户端隐藏玩家渲染。
     * 二者都是低频事件，挂在全局总线上没有性能问题。
     * </p>
     */
    private static class StealthEventHandler {
        private final UUID boundEntityId;

        public StealthEventHandler(LivingEntity entity) {
            this.boundEntityId = entity.getUUID();
        }

        /**
         * 阻止生物将隐身实体设为目标
         */
        @SubscribeEvent(priority = EventPriority.HIGHEST)
        public void onLivingChangeTarget(LivingChangeTargetEvent event) {
            if (event.getEntity().level().isClientSide()) return;
            if (event.getNewTarget() == null) return;
            if (!event.getNewTarget().getUUID().equals(boundEntityId)) return;

            if (DynamicAttributeManager.has(event.getNewTarget(), DynamicAttributes.STEALTH)) {
                event.setCanceled(true);
            }
        }

        /**
         * 客户端：隐藏玩家渲染
         * 使用客户端同步管理器检查是否应该渲染
         */
        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public void onRenderPlayer(RenderPlayerEvent.Pre event) {
            // 检查被渲染的玩家是否在隐身列表中（序列号4）
            if (ClientSyncEffectManager.shouldRenderEffect(4, event.getEntity().getId())) {
                event.setCanceled(true);
            }
        }
    }
}
