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
 * 睡眠「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} / {@link FrostbiteSyncHandler} / {@link HemorrhageSyncHandler}
 * / {@link IncisionSyncHandler} 完全同构，解决同一个问题：原版 {@link MobEffect} 只对
 * 「玩家自己」完整同步到客户端；对<b>其他实体</b>，原版仅在观察者开始追踪该实体的那一刻
 * 同步一次当时的效果，此后追踪期间的效果变化不再下发，导致 {@code hasEffect(SLEEP)}
 * 对怪物不可靠。
 * </p>
 * <p>
 * <b>本处理器对睡眠尤其关键。</b>睡眠几乎<b>总是施加给敌人</b>的——催眠烟雾
 * （{@code EnchantmentHypnoticSmoke}，攻击时概率触发）与托莉娜箭
 * （{@code EnchantmentHypnoticArrow}，箭矢命中时概率触发）都是对目标施加。
 * 而正在交战的目标几乎必然已被观察者追踪，因此若没有本处理器，
 * {@code hasEffect} 恒为 false，睡眠视觉将<b>一次都不会出现</b>——
 * 这与出血此前「完全没有特效」是同一个根因。
 * </p>
 * <p>
 * <b>另有一点值得注意：</b>睡眠中的实体本身会被施加原版失明效果
 * （见 {@code MobEffectSleep.applyEffectTick}），所以「被睡的玩家自己」其实看不见任何画面；
 * 睡眠视觉的真正受众是<b>旁观者与施法者</b>——他们需要一眼看出「这个目标睡着了、
 * 现在打它有伤害加成」。这进一步说明本同步链路是睡眠视觉的唯一有效路径。
 * </p>
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class SleepSyncHandler {

    /**
     * 睡眠的客户端同步序列号。
     * <p>1~3 为自定义火焰、4 为隐身、5 为猩红腐败、6 为重力力场、7 为冻伤、8 为出血、
     * 9 为切腹（见 {@code IncisionSyncHandler}），睡眠取 10，避免冲突。</p>
     */
    public static final int SLEEP_SERIAL = 10;

    private SleepSyncHandler() {
    }

    /**
     * 判断给定效果是否为睡眠。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是睡眠返回 true
     */
    private static boolean isSleep(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.SLEEP.get();
    }

    /**
     * 实体被添加效果时：若为睡眠，登记到客户端同步集合（增量广播给观察者）。
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
        if (inst != null && isSleep(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, SLEEP_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为睡眠，从客户端同步集合移除。
     * <p><b>覆盖「被打醒」这一关键路径：</b>{@code MobEffectSleep.onLivingDamage} 在睡眠者
     * 受击时会调用 {@code removeEffect} 立刻解除睡眠，该调用会触发本事件，
     * 从而让视觉与「觉醒」瞬间同步消失，不会残留。</p>
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isSleep(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, SLEEP_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为睡眠，从客户端同步集合移除。
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
        if (inst != null && isSleep(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, SLEEP_SERIAL);
        }
    }
}
