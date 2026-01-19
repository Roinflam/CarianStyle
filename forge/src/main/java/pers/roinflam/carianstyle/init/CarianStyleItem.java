package pers.roinflam.carianstyle.init;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Tiers;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;
import pers.roinflam.carianstyle.utils.Reference;

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
            DeferredRegister.create(ForgeRegistries.ITEMS, Reference.MOD_ID);

    /**
     * 魔法辉剑物品
     * <p>
     * 用于渲染 EntityGlintblades 实体
     * </p>
     */
    public static final RegistryObject<ItemGlintblades> GLINTBLADES =
            ITEMS.register("glintblades", () ->
                    new ItemGlintblades(
                            Tiers.DIAMOND,
                            new Item.Properties()
                            // 如果要添加到创造模式标签页，使用以下代码：
                            // .tab(CreativeModeTabs.COMBAT)
                    )
            );
}