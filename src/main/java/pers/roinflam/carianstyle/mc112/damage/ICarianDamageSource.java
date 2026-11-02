package pers.roinflam.carianstyle.mc112.damage;

import java.util.Set;

/**
 * 本模组新造的伤害源共有的信息
 * <p>
 * 1.20.1 的伤害类型写在数据包里（{@code data/carianstyle/damage_type/*.json} 加上原版标签），
 * 1.12.2 的 {@code DamageSource} 没有类型和标签，只有几个开关。这里把 1.20.1 那份数据带在伤害源对象上：
 * 类型 id、这个类型自带的标签（{@link DamageTags#is} 会读），以及「受伤阶段再无视护甲」标记（由 {@code mc112.hook.DamageStageHook} 处理）。
 * </p>
 * <p>
 * 两个实现类分别对应没有实体、有实体的情况：{@link CarianDamageSource}、{@link CarianEntityDamageSource}。
 * </p>
 */
public interface ICarianDamageSource {

    /**
     * @return 伤害类型 id（{@code NewDamageSource} 的常量，如 {@code carianstyle:hemorrhage}）
     */
    String typeId();

    /**
     * @return 这个类型在 1.20.1 数据包里带的标签（{@link DamageTags} 的常量），不可修改
     */
    Set<String> builtinTags();

    /**
     * 是否要在受伤阶段才打开「无视护甲」
     * <p>
     * 1.12.2 的「无视护甲」开关同时让盾牌失效（盾牌检查要求 {@code !isUnblockable()}）。
     * 需要「无视护甲、盾牌照挡」的带实体伤害源先不开这个开关，等盾牌检查过了、{@code LivingHurtEvent} 时再开。
     * 注意类型本身就在 1.20.1 {@code bypasses_armor} 里的不该用这个标记：1.20.1 的 {@code bypasses_shield}
     * 包含 {@code #bypasses_armor}，那种伤害盾牌本来就挡不住，构造时直接开开关即可。
     * </p>
     *
     * @return 受伤阶段要不要打开无视护甲
     */
    boolean bypassesArmorAtHurt();

    /**
     * @param value 受伤阶段要不要打开无视护甲
     */
    void setBypassesArmorAtHurt(boolean value);
}
