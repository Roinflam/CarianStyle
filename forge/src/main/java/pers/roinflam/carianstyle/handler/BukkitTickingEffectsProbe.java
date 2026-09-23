package pers.roinflam.carianstyle.handler;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 探测「CraftBukkit 系服务端（Mohist 及其衍生版）此刻正在给这个实体 tick 药水效果」。
 * <p>
 * <b>2026-09-23 修复：药水 tick 期间新加的效果被 IEffectModifier 附魔改写两次。</b>
 * </p>
 * <p>
 * <b>问题：</b>Mohist 的 {@code LivingEntity}：
 * <ul>
 *   <li>{@code addEffect(MobEffectInstance, Entity)}（SRG {@code m_147207_}）开头是
 *       {@code if (isTickingEffects) { effectsToProcess.add(new ProcessableEffect(inst, cause)); return true; }}，
 *       即药水 tick 期间的 addEffect 不立即生效，只入队。</li>
 *   <li>{@code tickEffects()}（SRG {@code m_21217_}）进循环前置 {@code isTickingEffects = true}，
 *       循环结束置 false，再对队列逐条 {@code addEffect(e.effect, e.cause)} 重放，最后 clear。</li>
 *   <li>重放链：{@code addEffect(inst, Cause)} → {@code addEffect(inst, (Entity) null, cause)}
 *       → {@code addEffect(inst, Entity)}，三处都是 invokevirtual（javap 核对 Mohist 构建产物
 *       {@code mohist-1.20.1-47.4.13.jar}）。</li>
 * </ul>
 * {@code MixinEffectModifier} 挂在 {@code m_147207_} 的 HEAD，早于上面那个入队判断，于是旧逻辑下：
 * 入队前改一次，入队的是改过的实例；重放时这个实例又从 HEAD 进来，再改一次。
 * 以 BeastRobust 默认数值为例（时长 ×0.4、等级 ×2+1）：时长变成 ×0.16，等级 a 变成 4a+3；Lucidity、BeastVitality 同理各叠两次。
 * 原版 Forge（单机）没有这套入队机制，只改一次——同一件装备在 Mohist 系服务端和单机上效果不同。
 * </p>
 * <p>
 * <b>这条路径在实际运行中确实会走到：</b>药水 tick 期间 {@code LivingEntity.m_21217_()} 会直接调
 * {@code LivingEntity.addEffect(Cause)} 重放入队的效果，说明药水 tick 期间确有效果入队，重放确实经过本钩子。
 * tick 循环里（非重放）经过 {@code m_147207_} 的调用链更常见
 * （典型链是 {@code MobEffectInstance.m_19552_}（tick）→ {@code m_19550_}（applyEffect）
 * → 各效果的 {@code applyEffectTick m_6742_} → {@code m_7292_} → {@code m_147207_}，常见的是 传世之刃 {@code HitDamageEffect}、
 * Goety {@code AuraEffect}；目标是自己还是周围实体，栈上看不出）。
 * 这里只用它说明重放路径确实存在，不涉及调用次数。
 * </p>
 * <p>
 * <b>修法：</b>钩子发现被加效果的实体 {@code isTickingEffects == true} 时原样返回，让<b>未改写</b>的实例入队；
 * 重放那次 {@code isTickingEffects} 已是 false（先置 false 再重放），经上面的调用链一定再进 HEAD 钩子，照常改一次。
 * 子类覆写 {@code addEffect(inst, Entity)}：调 super 的，入队和重放两次都会进到 super 里的钩子；
 * 不调 super 的，本来就既不入队也不经过钩子。所以每个入队的实例恰好在重放时被改一次，不会漏。
 * <br>
 * 为什么不反过来（入队时改、重放时跳过）：重放那次调用时标志已经是 false，单看标志分不出它和普通调用。要区分，
 * 要么往 Mohist 独有的重放循环里注入（那段代码原版 Forge 没有，{@code defaultRequire = 1} 下单机会直接崩），
 * 要么按对象身份记住「入队的是本模组新建的副本」、重放时认出来跳过（得多维护一张线程安全的弱引用身份表）。
 * 现在的做法不需要额外状态，还有个好处：重放那次与「非 tick 期间直接调用」走同一条路径、传入同一个未改实例，其它模组挂在 addEffect
 * 头部的钩子（整合包里 Cataclysm / Goety / Biomancy / EndingLib 都有）、Forge 的 Applicable/Added 事件、Bukkit 事件
 * 看到的都与平时直接调用时相同（旧逻辑下重放时事件里是改过两次的实例）。
 * </p>
 * <p>
 * <b>各种调用方：</b>判断读的是<b>被加效果的那个实体自己的</b>标志，和 Mohist 决定入队的条件是同一个字段：
 * <ul>
 *   <li>效果 tick / {@code MobEffectEvent.Expired} 处理器里给<b>正在 tick 的实体自己</b>加效果（任何模组）→ 入队 → 重放时改一次；</li>
 *   <li>给<b>别的实体</b>加 → 对方的标志是 false → 与平时一样当场改一次；</li>
 *   <li>Mohist 的 tickEffects 只 catch {@code ConcurrentModificationException}；若效果 tick 抛出别的异常，
 *       标志会停在 true、队列不清（Mohist 原有行为），此后该实体的 addEffect 全部入队，等下一次 tickEffects 跑完一起重放。
 *       本修法下这些入队的仍是未改实例，重放时各改一次，与正常情况一致。</li>
 *   <li>异步线程：Mohist 自己在 addEffect 里也是不加同步地读这个字段（队列还是 ArrayList），异步调用本来就是竞态。
 *       本钩子读到 true 时立刻返回，与 Mohist 那次读之间只隔着排在本钩子之后的其它模组 HEAD 注入和一次
 *       {@code AsyncCatcher.catchAsync()}；恰好在这之间主线程把标志翻成 false，
 *       这一次会一次都不改（旧逻辑在同一窗口里是改一次）。读到 false 时照常改写，改写期间标志翻成 true 的，
 *       仍会入队后再改一次，即改两次——与旧逻辑在同一窗口里的结果相同。</li>
 * </ul>
 * </p>
 * <p>
 * <b>与旧行为的其它差异：</b>改写时刻从入队推迟到重放（同一次 tickEffects 内、同一 tick），装备按重放时刻取；
 * 入队的是调用方传进来的原对象而不是本模组新建的副本，调用方若在同一次 tickEffects 内原地改这个对象，重放会读到改后的值
 * ——但没有 IEffectModifier 附魔的实体原本入队的就是原对象，这是 Mohist 已有的语义。本模组三个 modifyEffect 都返回新对象，不改入参。
 * </p>
 * <p>
 * <b>环境兼容：</b>本类不 {@code @Shadow} 任何 CraftBukkit 字段，只按名字反射查找一次并缓存 getter：
 * <ul>
 *   <li>原版 Forge（单机 / 局域网 / 纯 Forge 服）的 {@code LivingEntity} 没有这两个字段
 *       （核对 Loom 缓存的 forge-1.20.1-47.4.13 合并 jar），查找失败 → 恒返回 false → 与修复前完全相同；</li>
 *   <li>这两个字段是 CraftBukkit 加的，不在 SRG 映射表里，运行期（SRG 名环境）仍叫 {@code isTickingEffects} /
 *       {@code effectsToProcess}（核对过 Mohist 服务端 jar 里 LivingEntity 的补丁常量池）；</li>
 *   <li>两个字段都要在、且 {@code isTickingEffects} 是非静态 boolean 才启用，避免撞上别的混合端里同名但语义不同的字段；</li>
 *   <li>还要有 Mohist 特有的静态字段 {@code addEffectCause}（{@code AtomicReference<Cause>}）才启用。本修法成立的前提是
 *       「重放一定再经过 {@code m_147207_} 的 HEAD」，这只在 Mohist 的结构上核实过：入队判断和整个方法体都在 2 参的
 *       {@code m_147207_} 里，带 cause 的 3 参重载只把 cause 存进这个静态字段再转调 {@code m_147207_}，
 *       {@code m_147207_} 里的入队和 Bukkit 事件都从这个字段取 cause。CarianStyle 在 CurseForge 公开发布，
 *       别的 CraftBukkit 系混合端（未核实）
 *       若把入队判断放在别的重载里、重放也直接调那个重载，重放就不经过本钩子，入队那次放行会变成一次都不改；
 *       所以认不出 Mohist 结构时打一条 INFO、保持修复前行为（改动前是几次还是几次，不会变差）；</li>
 *   <li>{@code setAccessible}：FML / Mohist 的 securejarhandler 对没有 module-info 的 jar（含 minecraft）
 *       用 {@code ModuleDescriptor.newAutomaticModule}，所有包对所有模块开放（核对 securejarhandler-mohist-2.1.11 的
 *       {@code SimpleJarMetadata}）。万一仍失败，打一条 WARN 并退回修复前行为，不影响开服。</li>
 * </ul>
 * 调用链上有实体 tick / 效果 tick（部分混合端会对出异常的实体做保护性处理），读取出任何异常都当作 false，即退回修复前行为。
 * </p>
 *
 * @author RoinFlam
 */
final class BukkitTickingEffectsProbe {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * {@code (LivingEntity) -> boolean}，读 {@code LivingEntity.isTickingEffects}。
     * null 表示当前环境没有这套入队机制（原版 Forge）、不是 Mohist 结构或探测失败，此时 {@link #isTickingEffects} 恒为 false。
     * static final 的 MethodHandle 会被 JIT 当常量内联，读取开销与直接读字段相当。
     */
    @Nullable
    private static final MethodHandle IS_TICKING_EFFECTS = resolve();

    private BukkitTickingEffectsProbe() {
    }

    /**
     * @return true 表示 CraftBukkit 系服务端此刻正在 tick 该实体的药水效果：本次 addEffect 只会入队，
     * 稍后在 tickEffects 末尾重放时会再经过钩子；原版 Forge、非 Mohist 结构的混合端或探测失败时恒为 false
     */
    static boolean isTickingEffects(@NotNull LivingEntity entity) {
        MethodHandle getter = IS_TICKING_EFFECTS;
        if (getter == null) {
            return false;
        }
        try {
            return (boolean) getter.invokeExact(entity);
        } catch (Throwable t) {
            // 只可能是类型不符之类的意外；当作不在 tick，退回修复前行为（改一次，最坏情况是旧的改两次）
            return false;
        }
    }

    @Nullable
    private static MethodHandle resolve() {
        final Field flag;
        try {
            flag = LivingEntity.class.getDeclaredField("isTickingEffects");
            // 入队用的队列也要在，确认是 CraftBukkit 那套「tick 期间入队、结束后重放」的机制
            LivingEntity.class.getDeclaredField("effectsToProcess");
        } catch (NoSuchFieldException e) {
            // 原版 Forge：没有入队机制，保持原行为（这是单机/客户端的正常情况，不打日志）
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[CarianStyle] Failed to look up LivingEntity.isTickingEffects; "
                    + "effect modifiers keep the old behavior during effect ticking", t);
            return null;
        }

        if (flag.getType() != boolean.class || Modifier.isStatic(flag.getModifiers())) {
            LOGGER.warn("[CarianStyle] LivingEntity.isTickingEffects has unexpected shape ({} {}); "
                    + "effect modifiers keep the old behavior during effect ticking",
                    Modifier.toString(flag.getModifiers()), flag.getType().getName());
            return null;
        }

        // 只认 Mohist 结构（3 参重载经 addEffectCause 转调 m_147207_，重放必经本钩子）；别的混合端未核实，保持原行为
        try {
            LivingEntity.class.getDeclaredField("addEffectCause");
        } catch (NoSuchFieldException e) {
            LOGGER.info("[CarianStyle] CraftBukkit effect queue found but not the Mohist layout "
                    + "(no LivingEntity.addEffectCause); effect modifiers keep the old behavior during effect ticking");
            return null;
        } catch (Throwable t) {
            LOGGER.warn("[CarianStyle] Failed to look up LivingEntity.addEffectCause; "
                    + "effect modifiers keep the old behavior during effect ticking", t);
            return null;
        }

        try {
            flag.setAccessible(true);
            MethodHandle getter = MethodHandles.lookup().unreflectGetter(flag)
                    .asType(MethodType.methodType(boolean.class, LivingEntity.class));
            LOGGER.info("[CarianStyle] CraftBukkit effect queue detected (LivingEntity.isTickingEffects); "
                    + "effects added during effect ticking are modified once, on replay");
            return getter;
        } catch (Throwable t) {
            LOGGER.warn("[CarianStyle] Failed to access LivingEntity.isTickingEffects; "
                    + "effect modifiers keep the old behavior during effect ticking", t);
            return null;
        }
    }
}
