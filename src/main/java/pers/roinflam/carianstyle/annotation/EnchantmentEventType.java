package pers.roinflam.carianstyle.annotation;

/**
 * 附魔事件类型枚举
 * <p>
 * 定义附魔可以监听的游戏事件类型
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public enum EnchantmentEventType {
    /**
     * 生物受到攻击事件（最早触发，可取消）
     * <p>
     * 用于：格挡判定、伤害免疫、早期拦截
     * </p>
     */
    LIVING_ATTACK,

    /**
     * 生物受伤事件（护甲减免前）
     * <p>
     * 用于：伤害修正、附加效果、主要的伤害计算
     * </p>
     */
    LIVING_HURT,

    /**
     * 生物受到伤害事件（最终伤害值）
     * <p>
     * 用于：持续伤害效果、后处理逻辑
     * </p>
     */
    LIVING_DAMAGE,

    /**
     * 生物死亡事件
     * <p>
     * 用于：死亡触发效果、复活机制
     * </p>
     */
    LIVING_DEATH,

    /**
     * 生物治疗事件
     * <p>
     * 用于：治疗加成、治疗转换
     * </p>
     */
    LIVING_HEAL,

    /**
     * 玩家Tick事件
     * <p>
     * 用于：持续性效果、状态检查
     * </p>
     */
    PLAYER_TICK,

    /**
     * 弹射物碰撞事件
     * <p>
     * 用于：弓箭特效、投射物处理
     * </p>
     */
    PROJECTILE_IMPACT,

    /**
     * 药水效果添加事件
     * <p>
     * 用于：药水效果增强、效果转换
     * </p>
     */
    POTION_ADDED,

    /**
     * 暴击事件
     * <p>
     * 用于：暴击伤害修正
     * </p>
     */
    CRITICAL_HIT
}