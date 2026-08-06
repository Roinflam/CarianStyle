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
 * 出血「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} / {@link FrostbiteSyncHandler} 完全同构，解决的是同一个
 * 问题：原版 {@link MobEffect} 只对「玩家自己」完整同步到客户端；对<b>其他实体</b>（尤其战斗
 * 中途才被施加效果的怪物），原版仅在观察者<b>开始追踪该实体的那一刻</b>同步一次当时的效果，
 * 此后追踪期间该实体新增 / 移除 / 过期的效果不会再同步给观察者，导致
 * {@code hasEffect(HEMORRHAGE)} 对怪物不可靠。
 * </p>
 * <p>
 * <b>这正是「出血完全没有特效」的根因：</b>各种流血系附魔都是在战斗中途才通过
 * {@code target.addEffect(...)} 给目标加上出血——而观察者客户端此前几乎总是已经在追踪正在
 * 交战的目标（这是打架的常态），导致这次新增效果永远收不到同步，{@code hasEffect} 恒为
 * {@code false}，特效自然一次都不会触发，而不是「强度不够看不清」。
 * </p>
 * <p>
 * 复用 {@link ClientSyncEffectManager} 的增量 add/remove 广播 + 定期全量重同步机制，用独立
 * 序列号 {@link #HEMORRHAGE_SERIAL} 表示「该实体带出血」。{@code HemorrhageBloodRenderer} 改为
 * {@code hasEffect(HEMORRHAGE) || shouldRenderEffect(HEMORRHAGE_SERIAL, id)} 双重判定，
 * 即便本同步链路因故失效也能退回到「玩家自己」的可见水平，不会全黑。
 * </p>
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class HemorrhageSyncHandler {

    /**
     * 出血的客户端同步序列号。
     * <p>1~3 为自定义火焰、4 为隐身、5 为猩红腐败、6 为重力力场、7 为冻伤（见
     * {@code FrostbiteSyncHandler}），出血取 8，避免冲突。</p>
     */
    public static final int HEMORRHAGE_SERIAL = 8;

    private HemorrhageSyncHandler() {
    }

    /**
     * 判断给定效果是否为出血。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是出血返回 true
     */
    private static boolean isHemorrhage(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.HEMORRHAGE.get();
    }

    /**
     * 实体被添加效果时：若为出血，登记到客户端同步集合（增量广播给观察者）。
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
        if (inst != null && isHemorrhage(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, HEMORRHAGE_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为出血，从客户端同步集合移除。
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isHemorrhage(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, HEMORRHAGE_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为出血，从客户端同步集合移除。
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
        if (inst != null && isHemorrhage(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, HEMORRHAGE_SERIAL);
        }
    }
}
