package pers.roinflam.carianstyle.network;

import net.minecraft.world.entity.LivingEntity;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttribute;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;

import javax.annotation.Nonnull;

/**
 * 夏玻利利的嘶吼「层数可见性」同步辅助（服务端逻辑，双端加载安全）。
 * <p>
 * 与 {@link ScarletRotSyncHandler} 等 MobEffect 系同步器解决的是同一类问题——
 * 「观察者客户端看不到别的实体身上的状态」——但<b>触发链路完全不同</b>：
 * 嘶吼不是 {@code MobEffect}，而是 {@code DynamicAttributes.HOWL_SHABRIRI} 这个
 * {@link DynamicAttribute}，服务端把它存在
 * {@code DynamicAttributeManager.ENTITY_ATTRIBUTES} 里，<b>客户端那份 Map 恒为空</b>
 * （效果数据只在服务端产生），因此客户端连「这个目标有没有嘶吼」都不知道，
 * 更不用说叠了几层。
 * </p>
 *
 * <h3>为什么用「一层一个序列号」而不是新增带等级字段的包</h3>
 * <p>
 * {@link ClientSyncEffectManager} 的协议只同步「实体 ID → 序列号」，<b>不携带任何数值</b>。
 * 要让客户端知道层数，只有两条路：
 * </p>
 * <ol>
 *     <li><b>每层占一个序列号</b>（本实现）——零协议改动、零新增包，
 *         {@code VisualNetwork} / {@code NetworkHandler} 一行都不用碰；</li>
 *     <li>新增一个带等级字段的包 —— 更干净，但要在通道末尾追加注册、
 *         新写一套 manager，而这条链路只服务这一个附魔。</li>
 * </ol>
 * <p>
 * 层数上限只有 6 档（见下），序列号空间还很富余，故取方案一。
 * 代价是换层时会发一次 remove + 一次 add（两个约 13 字节的小包），
 * 而嘶吼叠层的频率受攻击速度限制，量级完全可以接受。
 * </p>
 *
 * <h3>⚠ 关于「6 档」而不是 5 档</h3>
 * <p>
 * 语言文件里嘶吼写的是「至多 5 层」，但 {@code EnchantmentHowlShabriri} 的实际逻辑是：
 * </p>
 * <pre>
 * int newAmplifier = currentAmplifier &lt; 0 ? 0 : Math.min(currentAmplifier + 1, 5);
 * </pre>
 * <p>
 * 首次施加 amplifier = 0，此后每次 +1、封顶 5，因此 amplifier 的取值是
 * <b>0~5 共 6 个</b>；而 {@code DynamicAttribute.ModifierConfig.calculate} 用的是
 * {@code baseValue × (amplifier + 1)}，即 amplifier 5 对应 -90% 护甲 = <b>第 6 层</b>。
 * </p>
 * <p>
 * <b>这是附魔本身的一处 off-by-one，本次不动它</b>（改数值会实打实改变平衡）。
 * 本类如实按 6 档同步，渲染器也按 6 档显示——玩家看到的层数与真实生效的减甲一致，
 * 只是与描述文本差一层。若你决定把附魔改成真正的 5 层封顶
 * （把 {@code Math.min(currentAmplifier + 1, 5)} 改成 {@code 4}），
 * 只需把 {@link #HOWL_MAX_AMPLIFIER} 同步改为 4，本类与渲染器都无需其它改动。
 * </p>
 *
 * <h3>接入方式</h3>
 * <p>
 * 不修改 {@code EnchantmentHowlShabriri} 一行代码——本类的两个方法作为
 * {@code DynamicAttribute} 的生命周期回调挂在 {@code DynamicAttributes} 的静态块里：
 * </p>
 * <pre>
 * HOWL_SHABRIRI
 *         .onApplied(HowlShabririSyncHelper::onApplied)
 *         .onRemoved(HowlShabririSyncHelper::onRemoved);
 * </pre>
 * <p>
 * 这样叠层、覆盖、自然过期、实体死亡（{@code clearAll}）全部路径都会被覆盖到，
 * 不会出现「附魔某条分支忘了同步」的漏网。
 * </p>
 *
 * <h3>回调时序（这里有个坑，已绕开）</h3>
 * <p>
 * {@code DynamicAttributeManager} 里 {@code triggerOnRemoved} 的调用时机<b>并不一致</b>：
 * </p>
 * <ul>
 *     <li>覆盖路径（{@code remove(entity, instance)}）——实例<b>还在</b>列表里就触发回调；</li>
 *     <li>过期路径（{@code processEntityTick}）——先 {@code iterator.remove()}
 *         <b>再</b>触发回调。</li>
 * </ul>
 * <p>
 * 也就是说在 {@link #onRemoved} 里调 {@code getAmplifier} 拿到的值时有时无。
 * 因此本类的移除路径<b>一律清空全部 6 档</b>，不去查当前层数——
 * {@code removeEntity} 对不在集合中的序列号是零开销空操作，清全部既安全又幂等。
 * </p>
 * <p>
 * 而 {@link #onApplied} 是在 {@code instances.add(instance)} <b>之后</b>触发的，
 * 此时 {@code getAmplifier} 必定返回新值，可以放心查。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class HowlShabririSyncHelper {

    /**
     * 嘶吼层数序列号的起始值：{@code serial = HOWL_SERIAL_BASE + amplifier}。
     * <p>
     * 已占用的序列号：1~3 自定义火焰、4 隐身、5 猩红腐败、6 重力力场、7 冻伤、
     * 8 出血、9 切腹、10 睡眠、11 噩兆、12 时间逆转
     * （见 {@code EnchantmentTimeReversal.TIME_REVERSAL_SERIAL}）。
     * 故嘶吼从 13 起，占用 13~18。
     * </p>
     */
    public static final int HOWL_SERIAL_BASE = 13;

    /**
     * 嘶吼 amplifier 的最大值。
     * <p>与 {@code EnchantmentHowlShabriri} 里
     * {@code Math.min(currentAmplifier + 1, 5)} 的封顶值保持一致；
     * 改动那边务必同步改这里（详见类注释的「⚠ 关于 6 档」小节）。</p>
     */
    public static final int HOWL_MAX_AMPLIFIER = 5;

    /** 层数档位总数（amplifier 0 ~ {@link #HOWL_MAX_AMPLIFIER}） */
    public static final int HOWL_TIER_COUNT = HOWL_MAX_AMPLIFIER + 1;

    private HowlShabririSyncHelper() {
    }

    /**
     * 由 amplifier 换算出对应的同步序列号。
     *
     * @param amplifier 嘶吼等级（0 ~ {@link #HOWL_MAX_AMPLIFIER}）
     * @return 序列号
     */
    public static int serialFor(int amplifier) {
        return HOWL_SERIAL_BASE + amplifier;
    }

    /**
     * 嘶吼被施加 / 层数变化时：把该实体登记到对应层数的序列号下，并清掉其余 5 档。
     * <p>
     * 「先清其余、再加当前」而不是「只加当前」——因为覆盖路径虽然会先触发
     * {@link #onRemoved}，但万一将来 {@code DynamicAttributeManager} 的时序改动
     * 导致 onRemoved 被跳过（例如新增了「同等级只刷新时长」之外的其它早退分支），
     * 只加不清会让同一实体同时挂着两档、渲染器读到错误层数。
     * 多余的 5 次 {@code removeEntity} 在不命中时是零开销的。
     * </p>
     *
     * @param entity    目标实体
     * @param attribute 触发回调的属性（即 {@code DynamicAttributes.HOWL_SHABRIRI}）
     */
    public static void onApplied(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        if (entity.level().isClientSide()) {
            return;
        }

        // onApplied 在 instances.add 之后触发，此处必定拿得到新值（详见类注释「回调时序」）
        int amplifier = DynamicAttributeManager.getAmplifier(entity, attribute);
        if (amplifier < 0) {
            // 理论上不会发生；真发生了说明属性已被移除，按清空处理最安全
            clearAllTiers(entity);
            return;
        }
        if (amplifier > HOWL_MAX_AMPLIFIER) {
            amplifier = HOWL_MAX_AMPLIFIER;
        }

        for (int i = 0; i < HOWL_TIER_COUNT; i++) {
            if (i != amplifier) {
                ClientSyncEffectManager.removeEntity(entity, serialFor(i));
            }
        }
        ClientSyncEffectManager.addEntity(entity, serialFor(amplifier));
    }

    /**
     * 嘶吼被移除 / 过期 / 实体死亡时：清空全部 6 档。
     * <p>不查当前层数，原因见类注释的「回调时序」小节。</p>
     *
     * @param entity    目标实体
     * @param attribute 触发回调的属性（未使用，保留以匹配回调签名）
     */
    public static void onRemoved(@Nonnull LivingEntity entity, @Nonnull DynamicAttribute attribute) {
        if (entity.level().isClientSide()) {
            return;
        }
        clearAllTiers(entity);
    }

    /**
     * 把该实体从全部 6 档序列号中移除。
     * <p>对不在集合中的序列号是零开销空操作，可重复调用。</p>
     *
     * @param entity 目标实体
     */
    private static void clearAllTiers(@Nonnull LivingEntity entity) {
        for (int i = 0; i < HOWL_TIER_COUNT; i++) {
            ClientSyncEffectManager.removeEntity(entity, serialFor(i));
        }
    }
}
