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
 * 切腹「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} / {@link FrostbiteSyncHandler} / {@link HemorrhageSyncHandler}
 * 完全同构，解决的是同一个问题：原版 {@link MobEffect} 只对「玩家自己」完整同步到客户端；对
 * <b>其他实体</b>（尤其战斗中途才被施加效果的怪物），原版仅在观察者<b>开始追踪该实体的那一刻</b>
 * 同步一次当时的效果，此后追踪期间该实体新增 / 移除 / 过期的效果不会再同步给观察者，导致
 * {@code hasEffect(INCISION)} 对怪物不可靠。
 * </p>
 * <p>
 * <b>与其它三个 SyncHandler 的重要差异：</b>切腹是<b>自身增益</b>——由持有者自己触发、加在自己身上。
 * 因此对「玩家自己进入切腹状态」这一最常见场景，原版同步本就完整可靠，
 * {@code IncisionRenderer} 单靠 {@code hasEffect} 就能正确显示，<b>不依赖本处理器</b>。
 * 本处理器只负责补上「观察其它实体（怪物 / 其他玩家）的切腹状态」这一档，属于锦上添花。
 * 换言之，即便本同步链路完全失效，你自己身上的切腹视觉也不会受影响。
 * </p>
 * <p>
 * 复用 {@link ClientSyncEffectManager} 的增量 add/remove 广播 + 定期全量重同步机制，用独立序列号
 * {@link #INCISION_SERIAL} 表示「该实体处于切腹状态」。
 * </p>
 * <p>
 * <b>已知局限（与既有三个 SyncHandler 一致）：</b>重新进入世界后实体网络 id 会重新分配，
 * {@link ClientSyncEffectManager#syncDimensionToPlayer} 中按旧 id 解析不到实体的条目会被剪枝，
 * 因此重进后需再次触发效果才会重新登记。对切腹而言影响很小——它是短时自身增益，
 * 玩家重进世界后本就需要重新触发。
 * </p>
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class IncisionSyncHandler {

    /**
     * 切腹的客户端同步序列号。
     * <p>1~3 为自定义火焰、4 为隐身、5 为猩红腐败、6 为重力力场、7 为冻伤、8 为出血，
     * 切腹取 9，避免冲突。</p>
     */
    public static final int INCISION_SERIAL = 9;

    private IncisionSyncHandler() {
    }

    /**
     * 判断给定效果是否为切腹。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是切腹返回 true
     */
    private static boolean isIncision(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.INCISION.get();
    }

    /**
     * 实体被添加效果时：若为切腹，登记到客户端同步集合（增量广播给观察者）。
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
        if (inst != null && isIncision(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, INCISION_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为切腹，从客户端同步集合移除。
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isIncision(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, INCISION_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为切腹，从客户端同步集合移除。
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
        if (inst != null && isIncision(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, INCISION_SERIAL);
        }
    }
}
