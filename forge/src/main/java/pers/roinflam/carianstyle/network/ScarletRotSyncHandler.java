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
 * 猩红腐败「客户端可见性」同步处理器（服务端逻辑，双端加载安全）。
 * <p>
 * <b>解决的问题：</b>原版 {@link MobEffect} 只对「玩家自己」完整同步到客户端；对<b>其他实体</b>
 * （尤其是怪物），原版仅在玩家<b>开始追踪该实体的那一刻</b>同步一次当时的效果列表，此后追踪期间
 * 该实体的效果变化（新加 / 移除 / 过期）<b>不会</b>再同步给观察者。因此战斗中给怪物施加猩红腐败后，
 * 观察者客户端的 {@code entity.hasEffect(SCARLET_ROT)} 仍为 {@code false}，导致
 * {@code ScarletRotMistRenderer} 无法据此渲染红雾——这正是「打怪上腐败却看不到红雾」的根因。
 * <p>
 * <b>解决方式：</b>复用项目已有的 {@link ClientSyncEffectManager}（与自定义火焰同款的
 * 「增量 add/remove 广播 + 每 5 秒全量重同步 + 登录 / 切维度同步」机制），用一个独立序列号
 * {@link #SCARLET_ROT_SERIAL} 表示「该实体带猩红腐败」。本处理器监听 Forge 的 {@link MobEffectEvent}：
 * <ul>
 *     <li>{@link MobEffectEvent.Added}：实体被加上猩红腐败 → {@link ClientSyncEffectManager#addEntity};</li>
 *     <li>{@link MobEffectEvent.Remove}：主动移除猩红腐败 → {@link ClientSyncEffectManager#removeEntity};</li>
 *     <li>{@link MobEffectEvent.Expired}：猩红腐败自然到期 → {@link ClientSyncEffectManager#removeEntity}.</li>
 * </ul>
 * 客户端由此维护「带腐败的实体集合」，{@code ScarletRotMistRenderer} 改用
 * {@code ClientSyncEffectManager.shouldRenderEffect(SCARLET_ROT_SERIAL, id)} 判定，对所有实体（含怪物）
 * 都准确。
 * <p>
 * <b>容错：</b>{@link ClientSyncEffectManager} 自带每 5 秒全量重同步，可修正偶发的增量丢包；其重同步
 * 还会剔除已死亡 / 卸载的实体，故无需在此额外处理实体死亡清理。理论边界：{@link MobEffectEvent.Remove}
 * 可被其它 mod 取消（极罕见），此时本处理器仍会发出一次移除——但由于猩红腐败常被反复施加 / 传播刷新，
 * 下一次 {@link MobEffectEvent.Added} 会重新登记，可自愈，观感无碍。
 * <p>
 * <b>性能：</b>仅对「效果恰为猩红腐败」的事件做处理，其余效果事件一次 == 比较即返回；
 * {@link ClientSyncEffectManager#addEntity} 内部对「已在集合中」的实体不重复广播，故永久腐败
 * （腐败女神等反复刷新）也不会产生重复网络包。所有逻辑仅在服务端执行（客户端事件由
 * {@code isClientSide} 守卫跳过）。
 *
 * @author FlameForge
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class ScarletRotSyncHandler {

    /**
     * 猩红腐败的客户端同步序列号。
     * <p>复用 {@link ClientSyncEffectManager} 的序列号体系：1~3 为自定义火焰、4 为隐身，
     * 故猩红腐败取 5，避免与既有序列号冲突。{@code ScarletRotMistRenderer} 引用此常量做渲染判定。</p>
     */
    public static final int SCARLET_ROT_SERIAL = 5;

    private ScarletRotSyncHandler() {
    }

    /**
     * 判断给定效果是否为猩红腐败。
     *
     * @param effect 待判断的效果（可为 null）
     * @return 是猩红腐败返回 true
     */
    private static boolean isScarletRot(MobEffect effect) {
        return effect != null && effect == CarianStylePotion.SCARLET_ROT.get();
    }

    /**
     * 实体被添加效果时：若为猩红腐败，登记到客户端同步集合（增量广播给观察者）。
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
        if (inst != null && isScarletRot(inst.getEffect())) {
            ClientSyncEffectManager.addEntity(entity, SCARLET_ROT_SERIAL);
        }
    }

    /**
     * 实体被主动移除效果时：若为猩红腐败，从客户端同步集合移除。
     *
     * @param event 效果移除事件
     */
    @SubscribeEvent
    public static void onRemove(@Nonnull MobEffectEvent.Remove event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) {
            return;
        }
        if (isScarletRot(event.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, SCARLET_ROT_SERIAL);
        }
    }

    /**
     * 实体效果自然到期时：若为猩红腐败，从客户端同步集合移除。
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
        if (inst != null && isScarletRot(inst.getEffect())) {
            ClientSyncEffectManager.removeEntity(entity, SCARLET_ROT_SERIAL);
        }
    }
}