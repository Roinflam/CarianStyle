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
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncAttribute;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttribute;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import java.util.List;
import java.util.UUID;

/**
 * 卡利亚风格模组 - 动态属性注册
 * 完全独立的效果系统，不依赖任何药水
 */
public class DynamicAttributes {

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
     */
    public static final DynamicAttribute CRAGBLADE = new DynamicAttribute("carianstyle_cragblade")
            .addModifier(Attributes.ATTACK_DAMAGE, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.KNOCKBACK_RESISTANCE, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .addModifier(Attributes.ARMOR_TOUGHNESS, 0.1, AttributeModifier.Operation.MULTIPLY_TOTAL)
            .withEventHandler(CragbladeEventHandler::new);

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
     * - 添加效果时清除周围32格所有生物的仇恨
     * - 客户端同步渲染：序列号4
     */
    public static final DynamicAttribute STEALTH = new DynamicAttribute("carianstyle_stealth")
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
    }

    // ========== 命名内部类：事件处理器 ==========

    /**
     * 岩石剑效果的事件处理器
     * 阻止实体跳跃
     */
    private static class CragbladeEventHandler {
        private final UUID boundEntityId;

        public CragbladeEventHandler(LivingEntity entity) {
            this.boundEntityId = entity.getUUID();
        }

        @SubscribeEvent
        public void onLivingUpdate(LivingEvent.LivingTickEvent event) {
            if (!event.getEntity().getUUID().equals(boundEntityId)) return;

            LivingEntity livingEntity = event.getEntity();
            if (DynamicAttributeManager.has(livingEntity, DynamicAttributes.CRAGBLADE)) {
                EntityLivingUtil.setJumped(livingEntity);
            }
        }
    }

    /**
     * 沙布里里的嚎叫效果的事件处理器
     * 减少治疗量
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
     */
    private static class StealthEventHandler {
        private static final double CLEAR_AGGRO_RANGE = 32.0;
        private final UUID boundEntityId;
        private boolean hasInitialized = false;

        public StealthEventHandler(LivingEntity entity) {
            this.boundEntityId = entity.getUUID();
        }

        /**
         * 首次Tick时清除周围仇恨
         */
        @SubscribeEvent
        public void onLivingTick(LivingEvent.LivingTickEvent event) {
            if (!event.getEntity().getUUID().equals(boundEntityId)) return;
            if (event.getEntity().level().isClientSide()) return;

            if (!hasInitialized) {
                hasInitialized = true;
                clearNearbyAggroTowards(event.getEntity());
            }
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

        /**
         * 清除周围生物对指定实体的仇恨
         */
        private void clearNearbyAggroTowards(LivingEntity target) {
            AABB searchBox = new AABB(
                    target.getX() - CLEAR_AGGRO_RANGE,
                    target.getY() - CLEAR_AGGRO_RANGE,
                    target.getZ() - CLEAR_AGGRO_RANGE,
                    target.getX() + CLEAR_AGGRO_RANGE,
                    target.getY() + CLEAR_AGGRO_RANGE,
                    target.getZ() + CLEAR_AGGRO_RANGE
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
    }
}