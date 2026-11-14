package pers.roinflam.carianstyle.fabric;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.BlockDropsEvent;
import pers.roinflam.carianstyle.event.BlockEvent;
import pers.roinflam.carianstyle.event.CriticalHitEvent;
import pers.roinflam.carianstyle.event.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.EntityLeaveLevelEvent;
import pers.roinflam.carianstyle.event.LivingChangeTargetEvent;
import pers.roinflam.carianstyle.event.LivingDeathEvent;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.LivingHealEvent;
import pers.roinflam.carianstyle.event.LivingKnockBackEvent;
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
 * 把 Fabric API 回调和 {@code carianstyle.fabric.logic.mixins.json} 里的 mixin 接到模组自带的事件总线上
 * （服务端和客户端都会跑的逻辑事件，含伤害三段）。
 * <p>
 * 事件都是 {@code event/impl} 的字段型实现，{@link CarianBus#post} 一次分发完五档。
 * 只接位置和语义都和 NeoForge 对得上的 7 个回调；对不上的（登录 / 下线 / 切维度 / 加入离开世界等）写成 mixin，
 * 注在 NeoForge 打补丁的同一处，见 {@code mixin/event}、{@code mixin/damage}。
 * </p>
 */
public final class FabricLogicEvents {

    /** 破坏方块回调里本模组用的阶段：排在默认阶段之后，别的模组（领地保护等）先取消的话就轮不到这里。 */
    private static final ResourceLocation BLOCK_BREAK_PHASE =
            ResourceLocation.fromNamespaceAndPath("carianstyle", "after_default");

    /** 自测入口（只在开发运行的 selftest 源码集里，正式 jar 没有这个类）。 */
    private static final String SELFTEST_LOGIC = "pers.roinflam.carianstyle.fabric.selftest.logic.LogicSelfTest";

    /** 内置数据包自测入口（同上，只在开发运行里有）。 */
    private static final String SELFTEST_PACK = "pers.roinflam.carianstyle.fabric.selftest.pack.PackSelfTest";

    private FabricLogicEvents() {
    }

    /**
     * 这里和逻辑类 mixin 发出的全部事件类，平台入口在加载完成前交给 {@link CarianBus#provide}。
     */
    public static List<Class<?>> forwardedEvents() {
        return Arrays.asList(
                LivingAttackEvent.class, LivingHurtEvent.class, LivingDamageEvent.class,
                LivingDeathEvent.class, LivingHealEvent.class, LivingKnockBackEvent.class,
                LivingExperienceDropEvent.class, LivingChangeTargetEvent.class, CriticalHitEvent.class,
                MobEffectEvent.Added.class, MobEffectEvent.Remove.class, MobEffectEvent.Expired.class,
                EntityJoinLevelEvent.class, EntityLeaveLevelEvent.class, ProjectileImpactEvent.class,
                PlayerEvent.PlayerLoggedInEvent.class, PlayerEvent.PlayerLoggedOutEvent.class,
                PlayerEvent.PlayerRespawnEvent.class, PlayerEvent.PlayerChangedDimensionEvent.class,
                PlayerEvent.Clone.class, PlayerTickEvent.Pre.class,
                ServerTickEvent.Pre.class, ServerTickEvent.Post.class,
                ServerStartedEvent.class, ServerStoppedEvent.class,
                BlockEvent.BreakEvent.class, BlockDropsEvent.class);
    }

    public static void init() {
        // 服务端 tick：Pre 在 tickChildren 之前（比 NeoForge 晚过 tickRateManager.tick()，现有监听不受影响），Post 在 tickServer 末尾
        ServerTickEvents.START_SERVER_TICK.register(server -> CarianBus.post(new ServerTickPreEventImpl(server)));
        ServerTickEvents.END_SERVER_TICK.register(server -> CarianBus.post(new ServerTickPostEventImpl(server)));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> CarianBus.post(new ServerStartedEventImpl(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CarianBus.post(new ServerStoppedEventImpl(server)));

        // 重生：PlayerList.respawn 末尾；alive = 从末地回来（没死），即 isEndConquered
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                CarianBus.post(new PlayerRespawnEventImpl(newPlayer, alive)));
        // 复制玩家数据：ServerPlayer.restoreFrom 末尾；alive 为假即死亡重生
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) ->
                CarianBus.post(new PlayerCloneEventImpl(newPlayer, oldPlayer, !alive)));

        // 破坏方块：ServerPlayerGameMode.destroyBlock 里原版拒绝条件之后（那些条件先返回，等于 NeoForge 的预取消），
        // 返回 false = 取消。用回调而不是自己写 mixin，才能排在领地类模组之后
        PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(Event.DEFAULT_PHASE, BLOCK_BREAK_PHASE);
        PlayerBlockBreakEvents.BEFORE.register(BLOCK_BREAK_PHASE, (level, player, pos, state, blockEntity) ->
                !CarianBus.post(new BreakEventImpl(level, pos, state, player)).isCanceled());

        installSelfTest();
    }

    /**
     * 开发运行带 {@code -Dcarianstyle.selftest=<组>}（Gradle 用 {@code -Pcarianstyle.selftest=<组>}，逗号分隔）时挂上自测：
     * {@code logic} 是逻辑事件自测，{@code pack} 是内置数据包自测。
     * 主代码编译期看不到 selftest 源码集，只能反射；不设开关时完全不碰它。
     */
    private static void installSelfTest() {
        String groups = System.getProperty("carianstyle.selftest");
        if (groups == null) {
            return;
        }
        List<String> names = Arrays.asList(groups.split(","));
        if (names.contains("logic")) {
            installSelfTest("logic", SELFTEST_LOGIC);
        }
        if (names.contains("pack")) {
            installSelfTest("pack", SELFTEST_PACK);
        }
    }

    private static void installSelfTest(String group, String className) {
        try {
            Class.forName(className).getMethod("install").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("carianstyle.selftest=" + group + " 但找不到自测类（只在开发运行里有）", e);
        }
    }
}
