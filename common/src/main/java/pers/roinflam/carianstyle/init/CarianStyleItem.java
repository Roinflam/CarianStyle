package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;
import pers.roinflam.carianstyle.CarianContent;
import net.minecraft.core.registries.Registries;

import java.util.function.Supplier;

/**
 * 模组物品注册类
 * <p>
 * 声明交给 {@link CarianContent}，由它在模组加载时触发本类、交给平台注册；字段在注册完成后才能 {@code get()}。
 * </p>
 */
public class CarianStyleItem {

    /**
     * 魔法辉剑物品
     * <p>
     * 用于渲染 EntityGlintblades 实体
     * </p>
     */
    public static final Supplier<ItemGlintblades> GLINTBLADES =
            CarianContent.register(Registries.ITEM, "glintblades", () ->
                    new ItemGlintblades(
                            Tiers.DIAMOND,
                            new Item.Properties()
                            // 如果要添加到创造模式标签页，使用以下代码：
                            // .tab(CreativeModeTabs.COMBAT)
                    )
            );
}