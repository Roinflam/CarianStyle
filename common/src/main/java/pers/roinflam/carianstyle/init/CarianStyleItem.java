package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraft.core.registries.Registries;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;

import java.util.function.Supplier;

/**
 * 模组物品注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义物品
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