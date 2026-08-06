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
 * 冻伤「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} 完全同构，解决的是同一个问题：原版 {@link MobEffect}
 * 只对「玩家自己」完整同步到客户端；对<b>其他实体</b>（尤其战斗中途才被施加效果的怪物），
 * 原版仅在观察者<b>开始追踪该实体的那一刻</b>同步一次当时的效果，此后追踪期间该实体新增/
 * 移除/过期的效果不会再同步给观察者，导致 {@code hasEffect(FROSTBITE)} 对怪物不可靠。
 * </p>
 * <p>
 * <b>这正是「月光剑打出的冻伤看不到特效」的根因：</b>亚杜拉的月光剑、辉石冰块等附魔都是在
 * 战斗中途才通过 {@code victim.addEffect(...)} 给目标加上冻伤——如果观察者客户端此前已经在
 * 追踪该实体（很常见，正在打的目标基本都已被追踪），就收不到这次新增效果的同步。
 * </p>
 * <p>
 * 复用 {@link ClientSyncEffectManager} 的增量 add/remove 广播 + 定期全量重同步机制，用独立
 * 序列号 {@link #FROSTBITE_SERIAL} 表示「该实体带冻伤」。{@code FrostbiteMistRenderer} 改为
 * {@code hasEffect(FROSTBITE) || shouldRenderEffect(FROSTBITE_SERIAL, id)} 双重判定，
 * 即便本同步链路因故失效也能退回到「玩家自己」的可见水平，不会全黑。
 * </p>
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class FrostbiteSyncHandler {

    /**
     * 冻伤的客户端同步序列号。
     * <p>1~3 为自定义火焰、4 为隐身、5 为猩红腐败（见 {@code ScarletRotSyncHandler}）、
     * 6 为重力力场（见 {@code EnchantmentGravitas.GRAVITY_FIELD_SERIAL}），
     * 冻伤取 7，避免冲突。</p>
     */
    public static final int FROSTBITE_SERIAL = 7;

    private FrostbiteSyncHandler() {
    }

    /**
     * 判断给定效果是否为冻伤。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是冻伤返回 true
     */
    private static boolean isFrostbite(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.FROSTBITE.get();
    }

    /**
     * 实体被添加效果时：若为冻伤，登记到客户端同步集合（增量广播给观察者）。
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
        if (inst != null && isFrostbite(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, FROSTBITE_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为冻伤，从客户端同步集合移除。
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isFrostbite(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, FROSTBITE_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为冻伤，从客户端同步集合移除。
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
        if (inst != null && isFrostbite(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, FROSTBITE_SERIAL);
        }
    }
}
