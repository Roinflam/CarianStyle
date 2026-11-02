package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.util.Reflect;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.StealthHitboxSyncHelper;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * 隐身实体不画影子和 F3+B 碰撞箱（客户端）
 * <p>
 * 1.20.1 用 mixin 在 {@code EntityRenderDispatcher.renderHitbox} / {@code renderShadow} 的开头按同步序列号取消。
 * 1.12.2 不用 mixin，借原版自己的判断条件：{@code RenderManager.renderEntity} 的顺序是
 * {@code doRender} → {@code doRenderShadowAndFire}（影子要求 {@code !isInvisible()} 且 {@code shadowSize > 0}）
 * → F3+B 时 {@code renderDebugBoundingBox}（要求 {@code !isInvisible()}）。玩家的 {@code RenderPlayerEvent.Pre}
 * 和生物的 {@code RenderLivingEvent.Post} 都在 {@code doRender} 里面、影子和碰撞箱之前，所以在这两个事件里
 * 临时把客户端实体的隐身标志置上、或者把渲染器的影子尺寸临时设 0，原版就会跳过影子 / 碰撞箱，
 * 帧末再原样改回去。
 * </p>
 * <ul>
 *     <li><b>隐身玩家（序列号 4）</b>：由 {@code ClientSyncStealthRenderer} 在取消 {@code RenderPlayerEvent.Pre}
 *         的同一个处理器里调 {@link #hideShadowAndHitbox}（同时挂着序列号 19）或 {@link #hideShadow}（只有序列号 4）。</li>
 *     <li><b>挂着序列号 19 的其它实体</b>：本类在 {@code RenderLivingEvent.Post} 里临时置隐身标志。
 *         Post 在模型画完之后发，模型照常显示，只是影子和碰撞箱不画。
 *         和 1.20.1 的小差别：这类实体的影子也会一起藏掉（1.20.1 只藏碰撞箱）。</li>
 *     <li><b>还原</b>：{@code RenderWorldLastEvent}（这一帧的实体都画完了）统一还原；{@code ClientTickEvent} 开头再兜底一次。
 *         影子尺寸是同一种玩家渲染器共用的，另外在下一次任何 {@code RenderPlayerEvent.Pre} 一开头先还原，
 *         免得下一个正常玩家没有影子。</li>
 * </ul>
 * <p>
 * {@code setInvisible} 只改客户端实体数据里标志位的第 5 位。服务端发来的实体数据在每帧开头的计划任务里处理，
 * 渲染中途不会被覆盖；这里只对原本不隐身的实体置位，还原时写回 false。
 * 副作用：同一帧里别的模组读这个实体的 {@code isInvisible()} 会读到 true（只到帧末）。
 * </p>
 * <p>
 * 本类引用 {@code network} 包的序列号常量，不引用 {@code enchantment/}、{@code visual/client/} 的具体类。
 * {@link #hideShadowAndHitbox} / {@link #hideShadow} 只给 {@code ClientSyncStealthRenderer} 调。
 * </p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class StealthRenderHook {

    /**
     * {@code Render.shadowSize}（protected）
     */
    @Nullable
    private static final Field SHADOW_SIZE = Reflect.fieldOrNull(Render.class, "shadowSize", "field_76989_e");

    /**
     * 这一帧临时置了隐身标志的实体（只在渲染线程用）
     */
    private static final List<Entity> INVISIBLE_MARKED = new ArrayList<>();

    /**
     * 影子尺寸被临时设 0 的渲染器和它原来的值
     */
    @Nullable
    private static Render<?> zeroedRender;
    private static float zeroedShadowSize;

    private static boolean warnedNoField = false;

    private StealthRenderHook() {
    }

    /**
     * 这一帧里这个实体既不画影子也不画碰撞箱（临时置客户端隐身标志）
     *
     * @param entity 客户端实体
     */
    public static void hideShadowAndHitbox(@Nonnull Entity entity) {
        if (entity.isInvisible()) {
            // 本来就隐身（原版药水等），原版已经不画影子和碰撞箱，也不需要还原
            return;
        }
        entity.setInvisible(true);
        INVISIBLE_MARKED.add(entity);
    }

    /**
     * 这一帧里这个渲染器画的下一个实体不画影子（临时把影子尺寸设 0）
     *
     * @param render 玩家渲染器
     */
    public static void hideShadow(@Nonnull Render<?> render) {
        Field field = SHADOW_SIZE;
        if (field == null) {
            if (!warnedNoField) {
                warnedNoField = true;
                LogUtil.warn("卡利亚式附魔 - 找不到 Render.shadowSize，隐身玩家的影子无法隐藏");
            }
            return;
        }
        restoreShadow();
        try {
            zeroedShadowSize = field.getFloat(render);
            field.setFloat(render, 0F);
            zeroedRender = render;
        } catch (IllegalAccessException e) {
            zeroedRender = null;
        }
    }

    /**
     * 把临时设 0 的影子尺寸改回原值
     */
    private static void restoreShadow() {
        Render<?> render = zeroedRender;
        if (render == null) {
            return;
        }
        zeroedRender = null;
        Field field = SHADOW_SIZE;
        if (field == null) {
            return;
        }
        try {
            field.setFloat(render, zeroedShadowSize);
        } catch (IllegalAccessException ignored) {
            // 设得进去就改得回来，这里不会走到
        }
    }

    /**
     * 还原这一帧改过的全部状态
     */
    private static void restoreAll() {
        restoreShadow();
        if (INVISIBLE_MARKED.isEmpty()) {
            return;
        }
        for (int i = 0; i < INVISIBLE_MARKED.size(); i++) {
            INVISIBLE_MARKED.get(i).setInvisible(false);
        }
        INVISIBLE_MARKED.clear();
    }

    /**
     * 任何玩家开始渲染前先把上一个隐身玩家改掉的影子尺寸还原（同一种玩家渲染器是共用的）。
     * <p>优先级最高，保证排在 {@code ClientSyncStealthRenderer}（默认优先级）之前。</p>
     *
     * @param event 玩家渲染前事件
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        restoreShadow();
    }

    /**
     * 生物模型画完之后：挂着序列号 19 的实体临时置隐身标志，影子和碰撞箱就不画了。
     * <p>序列号 19 只在服务端打开 {@code hideStealthHitbox} 时下发，客户端不读配置。
     * 隐身玩家的 {@code doRender} 已经被取消，走不到这里，由 {@code ClientSyncStealthRenderer} 处理。</p>
     *
     * @param event 生物渲染后事件
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?> event) {
        Entity entity = event.getEntity();
        if (ClientSyncEffectManager.shouldRenderEffect(StealthHitboxSyncHelper.STEALTH_HITBOX_SERIAL, entity.getEntityId())) {
            hideShadowAndHitbox(entity);
        }
    }

    /**
     * 这一帧的实体都画完了：统一还原
     *
     * @param event 世界渲染末尾事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        restoreAll();
    }

    /**
     * 兜底：万一某一帧没走到 {@code RenderWorldLastEvent}（渲染中途抛异常等），下一个客户端 tick 开头还原
     *
     * @param event 客户端 tick
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            restoreAll();
        }
    }
}
