package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.core.registries.Registries;

/**
 * 模组物品注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义物品
 * </p>
 */
public class CarianStyleItem {

    /**
     * 物品延迟注册器
     */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(Registries.ITEM, Reference.MOD_ID);

    /**
     * 魔法辉剑物品
     * <p>
     * 用于渲染 EntityGlintblades 实体
     * </p>
     */
    public static final DeferredHolder<Item, ItemGlintblades> GLINTBLADES =
            ITEMS.register("glintblades", () ->
                    new ItemGlintblades(
                            Tiers.DIAMOND,
                            new Item.Properties()
                            // 如果要添加到创造模式标签页，使用以下代码：
                            // .tab(CreativeModeTabs.COMBAT)
                    )
            );
}