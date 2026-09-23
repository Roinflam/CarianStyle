package pers.roinflam.carianstyle.network;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import pers.roinflam.carianstyle.config.ConfigLoader;

import javax.annotation.Nonnull;

/**
 * 隐身玩家的碰撞箱屏蔽同步（服务端 -&gt; 客户端）。
 *
 * <h3>要解决的问题：F3+B 看穿隐身</h3>
 * <p>
 * {@code DynamicAttributes.STEALTH} 是本模组自己的效果，不走原版隐身药水。
 * 它在客户端只做了一件事：取消 {@code RenderPlayerEvent.Pre}，让玩家模型不渲染
 * （{@code ClientSyncStealthRenderer}，查序列号 4）。
 * 但原版的碰撞箱显示（F3+B）<b>不在模型渲染里</b>——它由
 * {@code EntityRenderDispatcher.render} 在调用完实体渲染器之后单独绘制，
 * 判定条件只有 {@code renderHitBoxes && !entity.isInvisible() && !showOnlyReducedInfo()}。
 * 本模组的隐身从不设置原版的隐身标志，于是隐身者的碰撞箱照画不误，
 * PvP 中任何人按一下 F3+B 就能看见隐身的人。
 * </p>
 *
 * <h3>为什么不直接设置原版隐身标志</h3>
 * <ul>
 *     <li>原版 {@code LivingEntity.updateInvisibilityStatus} 会在药水效果变化时
 *         执行 {@code setInvisible(hasEffect(INVISIBILITY))}，
 *         隐身期间身上任何一个药水效果增删都会把标志冲掉；</li>
 *     <li>该标志还参与 {@code LivingEntity.getVisibilityPercent}（生物的索敌距离），
 *         会顺带改变隐身的玩法数值。</li>
 * </ul>
 * <p>
 * 因此改为在客户端 {@code MixinEntityRenderDispatcher} 里拦截
 * {@code renderHitbox}，由本类把「哪些实体的碰撞箱要藏」同步下去。
 * </p>
 *
 * <h3>为什么用单独的序列号，而不是「序列号 4 + 下发一个配置开关」</h3>
 * <p>
 * 开关由服务端配置 {@link ConfigLoader#hideStealthHitbox} 决定。
 * 若让客户端自己判断「在隐身列表里 且 开关为真」，就得新增一个下发配置的数据包，
 * 进而要升级 {@code NetworkHandler} 的协议版本、处理登录下发与断线复位。
 * </p>
 * <p>
 * 改为由服务端逐实体决定：开关打开时，隐身玩家除了序列号 4（隐藏模型）之外
 * 再挂上 {@link #STEALTH_HITBOX_SERIAL}；客户端只看这一个序列号。
 * 这样完全复用 {@code ClientSyncEffectManager} 现成的增量广播、
 * 5 秒定期重同步、切维度覆盖与死亡清除，不新增任何数据包；
 * 多人游戏时客户端本地的配置也不可能影响结果。
 * </p>
 *
 * <h3>挂载点</h3>
 * <p>
 * 由 {@code DynamicAttributes.STEALTH} 的三个回调驱动：
 * </p>
 * <ul>
 *     <li>{@code onApplied} → {@link #sync}：进入隐身时按当前开关挂上 / 不挂；</li>
 *     <li>{@code onTick}（每 10 tick）→ {@link #sync}：服主热重载配置后，
 *         正在隐身的玩家也能在半秒内跟上新开关。
 *         {@code addEntity} / {@code removeEntity} 在状态不变时是不发包的空操作，
 *         所以这里的周期调用不会产生任何网络流量；</li>
 *     <li>{@code onRemoved} → {@link #clear}：退出隐身时无条件摘除，
 *         不看开关——避免「隐身中途关掉开关」后残留。</li>
 * </ul>
 *
 * <h3>只作用于玩家</h3>
 * <p>
 * 客户端的模型隐藏只挂在 {@code RenderPlayerEvent} 上（{@code ClientSyncStealthRenderer}），怪物带隐身时模型照常可见。
 * 碰撞箱屏蔽与之对齐：只对玩家生效，不会出现「怪物看得见却没有碰撞箱」的错位。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class StealthHitboxSyncHelper {

    /**
     * 「该实体的碰撞箱对所有客户端隐藏」的同步序列号。
     * <p>
     * 已占用的序列号：1~3 自定义火焰、4 隐身、5 猩红腐败、6 重力力场、7 冻伤、
     * 8 出血、9 切腹、10 睡眠、11 噩兆、12 时间逆转、13~18 嘶吼层数
     * （见 {@code HowlShabririSyncHelper.HOWL_SERIAL_BASE}）。故本类取 19。
     * </p>
     */
    public static final int STEALTH_HITBOX_SERIAL = 19;

    private StealthHitboxSyncHelper() {
    }

    /**
     * 按当前配置同步某个隐身实体的碰撞箱屏蔽状态（仅服务端）。
     * <p>
     * 幂等：状态没有变化时不发包，可以放心周期调用。
     * </p>
     *
     * @param entity 处于隐身状态的实体
     */
    public static void sync(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        if (ConfigLoader.hideStealthHitbox && entity instanceof Player) {
            ClientSyncEffectManager.addEntity(entity, STEALTH_HITBOX_SERIAL);
        } else {
            ClientSyncEffectManager.removeEntity(entity, STEALTH_HITBOX_SERIAL);
        }
    }

    /**
     * 实体退出隐身：无条件摘除碰撞箱屏蔽（仅服务端）。
     *
     * @param entity 退出隐身的实体
     */
    public static void clear(@Nonnull LivingEntity entity) {
        if (entity.level().isClientSide) {
            return;
        }
        ClientSyncEffectManager.removeEntity(entity, STEALTH_HITBOX_SERIAL);
    }
}
