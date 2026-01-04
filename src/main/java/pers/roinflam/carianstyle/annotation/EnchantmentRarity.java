package pers.roinflam.carianstyle.annotation;

/**
 * 附魔稀有度枚举
 * <p>
 * 定义附魔的稀有程度，决定最大等级和附魔能力公式
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public enum EnchantmentRarity {
    /**
     * 普通稀有度
     * <p>
     * 最大等级：5
     * 默认公式：5 + (level - 1) * 10
     * </p>
     */
    UNCOMMON(5, 5, 10),

    /**
     * 稀有
     * <p>
     * 最大等级：3
     * 默认公式：10 + (level - 1) * 15
     * </p>
     */
    RARE(3, 10, 15),

    /**
     * 极其稀有
     * <p>
     * 最大等级：1
     * 默认公式：使用RECOLLECT_ENCHANTABILITY常量
     * </p>
     */
    VERY_RARE(1, 38, 0);

    private final int maxLevel;
    private final int baseEnchantability;
    private final int levelMultiplier;

    EnchantmentRarity(int maxLevel, int baseEnchantability, int levelMultiplier) {
        this.maxLevel = maxLevel;
        this.baseEnchantability = baseEnchantability;
        this.levelMultiplier = levelMultiplier;
    }

    /**
     * 获取此稀有度的默认最大等级
     *
     * @return 最大等级
     */
    public int getMaxLevel() {
        return maxLevel;
    }

    /**
     * 获取此稀有度的基础附魔能力值
     *
     * @return 基础附魔能力值
     */
    public int getBaseEnchantability() {
        return baseEnchantability;
    }

    /**
     * 获取此稀有度的等级倍率
     *
     * @return 等级倍率
     */
    public int getLevelMultiplier() {
        return levelMultiplier;
    }

    /**
     * 计算指定等级的附魔能力需求
     *
     * @param level 附魔等级
     * @param difficultyMultiplier 难度倍率
     * @return 附魔能力需求值
     */
    public int calculateEnchantability(int level, double difficultyMultiplier) {
        if (this == VERY_RARE) {
            return (int) (baseEnchantability * difficultyMultiplier);
        }
        return (int) ((baseEnchantability + (level - 1) * levelMultiplier) * difficultyMultiplier);
    }
}