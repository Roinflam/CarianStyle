package pers.roinflam.carianstyle.init;

import net.minecraft.item.Item;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;
import pers.roinflam.carianstyle.mc112.registry.DeferredRegister;
import pers.roinflam.carianstyle.mc112.registry.RegistryObject;
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
            DeferredRegister.create(Item.class, Reference.MOD_ID);

    /**
     * 魔法辉剑物品
     * <p>
     * 用于渲染 EntityGlintblades 实体，不放进创造模式标签页
     * </p>
     */
    public static final RegistryObject<ItemGlintblades> GLINTBLADES =
            ITEMS.register("glintblades", () -> new ItemGlintblades(Item.ToolMaterial.DIAMOND));
}
