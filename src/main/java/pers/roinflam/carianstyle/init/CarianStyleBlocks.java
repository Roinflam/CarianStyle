package pers.roinflam.carianstyle.init;

import net.minecraft.block.Block;
import pers.roinflam.carianstyle.block.fire.CrimsonFlame;
import pers.roinflam.carianstyle.block.fire.WhiteFlame;
import pers.roinflam.carianstyle.block.fire.YellowFlame;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.mc112.registry.DeferredRegister;
import pers.roinflam.carianstyle.mc112.registry.RegistryObject;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组方块注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义方块
 * </p>
 * <p>
 * 1.12.2 版：注册器是 {@code mc112.registry} 的同名实现；方块属性（亮度、声音、无碰撞）写在各方块类的构造器里，
 * 火焰方块没有物品形态，光源方块也没有。
 * </p>
 */
public class CarianStyleBlocks {

    /**
     * 方块延迟注册器
     */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Block.class, Reference.MOD_ID);

    /**
     * 猩红火焰方块
     */
    public static final RegistryObject<CrimsonFlame> CRIMSON_FLAME = BLOCKS.register("crimson_flame", CrimsonFlame::new);

    /**
     * 白色火焰方块
     */
    public static final RegistryObject<WhiteFlame> WHITE_FLAME = BLOCKS.register("white_flame", WhiteFlame::new);

    /**
     * 黄色火焰方块
     */
    public static final RegistryObject<YellowFlame> YELLOW_FLAME = BLOCKS.register("yellow_flame", YellowFlame::new);

    /**
     * 隐藏光源方块
     */
    public static final RegistryObject<HideLight> HIDE_LIGHT = BLOCKS.register("hide_light", HideLight::new);
}
