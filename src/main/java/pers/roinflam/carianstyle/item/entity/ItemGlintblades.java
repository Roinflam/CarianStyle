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
        // 1.21.1：攻击伤害 / 攻速不再是构造参数，改由物品属性的属性修饰符组件提供，数值同 1.20.1（+3、-2.4）
        super(tier, properties.attributes(SwordItem.createAttributes(tier, 3, -2.4F)));
    }
}