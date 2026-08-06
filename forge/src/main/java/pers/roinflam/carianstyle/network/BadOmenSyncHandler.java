package pers.roinflam.carianstyle.network;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 噩兆「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} / {@link FrostbiteSyncHandler} / {@link HemorrhageSyncHandler}
 * / {@link IncisionSyncHandler} / {@link SleepSyncHandler} 完全同构：原版 {@link MobEffect}
 * 只对「玩家自己」完整同步到客户端，对其他实体在观察者开始追踪后的效果变化不再下发，
 * 导致 {@code hasEffect(BAD_OMEN)} 对怪物不可靠。
 * </p>
 * <p>
 * <b>噩兆与睡眠一样、几乎总是施加给敌人的</b>——由 {@code EnchantmentBadOmen} 在攻击时施加，
 * 且该附魔的核心机制正是「对已带噩兆的目标额外造成 50% 伤害」，也就是说玩家需要能
 * <b>一眼看出目标身上有没有噩兆</b>，才知道下一击是否吃到加成。若没有本同步链路，
 * 这个视觉在实战中一次都不会出现，附魔的核心节奏就完全没有反馈。
 * </p>
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class BadOmenSyncHandler {

    /**
     * 噩兆的客户端同步序列号。
     * <p>1~3 为自定义火焰、4 为隐身、5 为猩红腐败、6 为重力力场、7 为冻伤、8 为出血、
     * 9 为切腹、10 为睡眠（见 {@code SleepSyncHandler}），噩兆取 11，避免冲突。</p>
     */
    public static final int BAD_OMEN_SERIAL = 11;

    private BadOmenSyncHandler() {
    }

    /**
     * 判断给定效果是否为噩兆。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是噩兆返回 true
     */
    private static boolean isBadOmen(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.BAD_OMEN.get();
    }

    /**
     * 实体被添加效果时：若为噩兆，登记到客户端同步集合（增量广播给观察者）。
     *
     * @param event 效果添加事件
     */
    @SubscribeEvent
    public static void onAdded(@Nonnull MobEffectEvent.Added event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && isBadOmen(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, BAD_OMEN_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为噩兆，从客户端同步集合移除。
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isBadOmen(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, BAD_OMEN_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为噩兆，从客户端同步集合移除。
     *
     * @param event 效果到期事件
     */
    @SubscribeEvent
    public static void onExpired(@Nonnull MobEffectEvent.Expired event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        MobEffectInstance inst = event.getEffectInstance();
        if (inst != null && isBadOmen(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, BAD_OMEN_SERIAL);
        }
    }
}
