package pers.roinflam.carianstyle.item.entity;

import net.minecraft.item.ItemSword;

import javax.annotation.Nonnull;

/**
 * 魔法剑物品
 * <p>
 * 用于EntityGlintblades实体的渲染模型，不放进创造模式标签页
 * </p>
 */
public class ItemGlintblades extends ItemSword {

    /**
     * 构造函数
     *
     * @param material 物品材质等级（1.20.1 的 {@code Tier}）
     */
    public ItemGlintblades(@Nonnull ToolMaterial material) {
        super(material);
    }
}
