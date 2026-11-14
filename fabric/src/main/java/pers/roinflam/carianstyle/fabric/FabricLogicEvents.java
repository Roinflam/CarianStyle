package pers.roinflam.carianstyle.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.BlockEvent;
import pers.roinflam.carianstyle.event.CriticalHitEvent;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
import pers.roinflam.carianstyle.event.LivingTickEvent;
import pers.roinflam.carianstyle.event.MobEffectEvent;
import pers.roinflam.carianstyle.event.PlayerEvent;
import pers.roinflam.carianstyle.event.PlayerTickEvent;
import pers.roinflam.carianstyle.event.ProjectileImpactEvent;
import pers.roinflam.carianstyle.event.ServerStartedEvent;
import pers.roinflam.carianstyle.event.ServerStoppedEvent;
import pers.roinflam.carianstyle.event.ServerTickEvent;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.event.impl.BreakEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerCloneEventImpl;
import pers.roinflam.carianstyle.event.impl.PlayerRespawnEventImpl;
import pers.roinflam.carianstyle.event.impl.ServerStartedEventImpl;
import pers.roinflam.carianstyle.event.impl.ServerStoppedEventImpl;
import pers.roinflam.carianstyle.event.impl.ServerTickPostEventImpl;
import pers.roinflam.carianstyle.event.impl.ServerTickPreEventImpl;

import java.util.Arrays;
import java.util.List;

/**
 * 逻辑事件（含伤害三段）在 Fabric 上的发出：7 个位置和语义都和 Forge 对得上的 Fabric API 回调接在这里，
 * 其余写成 mixin，注在 Forge 打补丁的同一处（{@code mixin/event}、{@code mixin/damage}）。
 */
public final class FabricLogicEvents {

    // 排在默认阶段之后，领地保护这类模组先取消的话就轮不到这里
    private static final ResourceLocation BLOCK_BREAK_PHASE = new ResourceLocation("carianstyle", "after_default");

    // 只在开发运行的 selftest 源码集里有，正式 jar 没有
    private static final String SELFTEST_LOGIC = "pers.roinflam.carianstyle.fabric.selftest.logic.LogicSelfTest";

    private FabricLogicEvents() {
    }

    /** 这里和逻辑类 mixin 发的全部事件类，平台入口在加载完成前交给 {@link CarianBus#provide}。 */
    public static List<Class<?>> forwardedEvents() {
        return Arrays.asList(
                LivingAttackEvent.class, LivingHurtEvent.class, LivingDamageEvent.class,
                LivingDeathEvent.class, LivingHealEvent.class, LivingKnockBackEvent.class,
                LivingExperienceDropEvent.class, LivingChangeTargetEvent.class, CriticalHitEvent.class,
                MobEffectEvent.Added.class, MobEffectEvent.Remove.class, MobEffectEvent.Expired.class,
                EntityJoinLevelEvent.class, EntityLeaveLevelEvent.class, ProjectileImpactEvent.class,
                PlayerEvent.PlayerLoggedInEvent.class, PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.PlayerRespawnEvent.class, PlayerEvent.PlayerChangedDimensionEvent.class,
                PlayerEvent.Clone.class, PlayerTickEvent.Pre.class, LivingTickEvent.class,
                ServerTickEvent.Pre.class, ServerTickEvent.Post.class,
                ServerStartedEvent.class, ServerStoppedEvent.class,
                BlockEvent.BreakEvent.class);
    }

    public static void init() {
        // Pre 在 tickChildren 之前；Forge 在 tickCount++ 之前发，这里晚过自增，读 getTickCount() 会差 1，现有监听都不读
        ServerTickEvents.START_SERVER_TICK.register(server -> CarianBus.post(new ServerTickPreEventImpl(server)));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            CarianBus.post(new ServerTickPostEventImpl(server));
            RockBlasterExperience.clear();
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CarianBus.post(new ServerStartedEventImpl(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CarianBus.post(new ServerStoppedEventImpl(server)));

        // alive = 从末地回来（没死），即 isEndConquered
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CarianBus.post(new PlayerRespawnEventImpl(newPlayer, alive)));
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                CarianBus.post(new PlayerCloneEventImpl(newPlayer, oldPlayer, !alive)));

        // 原版的拒绝条件先返回，等于 Forge 的预取消；返回 false = 取消
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(Event.DEFAULT_PHASE, BLOCK_BREAK_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(BLOCK_BREAK_PHASE, (level, player, pos, state, blockEntity) ->
                !CarianBus.post(new BreakEventImpl(level, pos, state, player)).isCanceled());

        installSelfTest();
    }

    /** 开发运行带 {@code -Dcarianstyle.selftest=logic} 时挂上自测。主代码编译期看不到 selftest 源码集，只能反射。 */
    private static void installSelfTest() {
        String groups = System.getProperty("carianstyle.selftest");
        if (groups == null || !Arrays.asList(groups.split(",")).contains("logic")) {
            return;
        }
        try {
            Class.forName(SELFTEST_LOGIC).getMethod("install").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("carianstyle.selftest=logic 但找不到自测类（只在开发运行里有）", e);
        }
    }
}
