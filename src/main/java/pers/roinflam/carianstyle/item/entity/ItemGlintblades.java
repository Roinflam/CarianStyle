package pers.roinflam.carianstyle.item.entity;

import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

import javax.annotation.Nonnull;

/**
 * 魔法剑物品
 * <p>
 * 用于EntityGlintblades实体的渲染模型
 * </p>
 */
public class ItemGlintblades extends SwordItem {

    /**
     * 构造函数
     *
     * @param tier 物品材质等级
     * @param properties 物品属性
     */
    public ItemGlintblades(@Nonnull Tier tier, @Nonnull Properties properties) {
        // 1.20.1: SwordItem 构造函数参数变化
        // (Tier, int attackDamage, float attackSpeed, Properties)
        super(tier, 3, -2.4F, properties);
    }
}