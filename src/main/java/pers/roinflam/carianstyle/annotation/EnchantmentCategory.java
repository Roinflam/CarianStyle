package pers.roinflam.carianstyle.annotation;

/**
 * 附魔类别枚举
 * <p>
 * 定义附魔所属的分类，同类别的附魔默认互斥
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public enum EnchantmentCategory {
    /**
     * 战技类附魔
     * <p>
     * 包含各种战斗技能类附魔，如盾击、狮子斩等
     * </p>
     */
    COMBAT_SKILL,

    /**
     * 追忆类附魔
     * <p>
     * 代表强大的记忆力量，包含最强大的附魔效果
     * </p>
     */
    RECOLLECT,

    /**
     * 律法类附魔
     * <p>
     * 体现不同的法则之力
     * </p>
     */
    LAW,

    /**
     * 死亡类附魔
     * <p>
     * 在生死边缘发挥作用的附魔
     * </p>
     */
    DEAD,

    /**
     * 通用类附魔
     * <p>
     * 不属于特定类别，不自动与其他附魔冲突
     * </p>
     */
    GENERAL
}