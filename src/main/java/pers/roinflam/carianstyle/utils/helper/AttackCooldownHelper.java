package pers.roinflam.carianstyle.utils.helper;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 「消耗这一下的蓄力」
 * <p>
 * 1.12.2 的满蓄力判断（{@code EnchantmentBase.isFullyCharged}）读的是出手前一 tick 记下的冷却，
 * 同一下挥击里中途清零不会影响排在后面的判断。这里只管两件事：
 * </p>
 * <ul>
 *   <li>{@link #spendSwing}：一下挥击只结算一次（横扫打中多个目标时只算第一个），二连斩、居合用；</li>
 *   <li>{@link #resetNextTick}：下一个服务端 tick 开头清零玩家的攻击冷却。</li>
 * </ul>
 */
@Mod.EventBusSubscriber
public class AttackCooldownHelper {

    /** 本 tick 已经结算过一次的玩家 */
    private static final Set<EntityPlayer> SPENT = Collections.newSetFromMap(new IdentityHashMap<>());

    /** 下个 tick 开头要清零冷却的玩家 */
    private static final Set<EntityPlayer> PENDING = Collections.newSetFromMap(new IdentityHashMap<>());

    private AttackCooldownHelper() {
    }

    /**
     * 这一下挥击还没结算过就记下并返回 true，同一 tick 再来返回 false；记下的同时安排下个 tick 清零冷却
     *
     * @param player 玩家
     * @return 是这一下挥击的第一次结算时为 true
     */
    public static boolean spendSwing(@Nonnull EntityPlayer player) {
        if (player.world.isRemote) {
            return true;
        }
        if (!SPENT.add(player)) {
            return false;
        }
        PENDING.add(player);
        return true;
    }

    /**
     * 在下一个服务端 tick 开头清零玩家的攻击冷却
     *
     * @param player 玩家
     */
    public static void resetNextTick(@Nonnull EntityPlayer player) {
        if (!player.world.isRemote) {
            PENDING.add(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTick(@Nonnull TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        SPENT.clear();
        if (PENDING.isEmpty()) {
            return;
        }
        for (EntityPlayer player : PENDING) {
            if (!player.isDead) {
                EntityLivingUtil.setTicksSinceLastSwing(player, 0);
            }
        }
        PENDING.clear();
    }
}
