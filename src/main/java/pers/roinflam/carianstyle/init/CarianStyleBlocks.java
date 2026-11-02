package pers.roinflam.carianstyle.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.carianstyle.block.fire.CrimsonFlame;
import pers.roinflam.carianstyle.block.fire.WhiteFlame;
import pers.roinflam.carianstyle.block.fire.YellowFlame;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.core.registries.Registries;

/**
 * 模组方块注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义方块
 * </p>
 */
public class CarianStyleBlocks {

    /**
     * 方块延迟注册器
     */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, Reference.MOD_ID);

    /**
     * 猩红火焰方块
     */
    public static final DeferredHolder<Block, CrimsonFlame> CRIMSON_FLAME = BLOCKS.register("crimson_flame", () ->
            new CrimsonFlame(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .noCollission()
                            .lightLevel(state -> 15)
                            .sound(SoundType.WOOL)
            )
    );

    /**
     * 白色火焰方块
     */
    public static final DeferredHolder<Block, WhiteFlame> WHITE_FLAME = BLOCKS.register("white_flame", () ->
            new WhiteFlame(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .noCollission()
                            .lightLevel(state -> 15)
                            .sound(SoundType.WOOL)
            )
    );

    /**
     * 黄色火焰方块
     */
    public static final DeferredHolder<Block, YellowFlame> YELLOW_FLAME = BLOCKS.register("yellow_flame", () ->
            new YellowFlame(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.FIRE)
                            .noCollission()
                            .lightLevel(state -> 15)
                            .sound(SoundType.WOOL)
            )
    );

    /**
     * 隐藏光源方块
     */
    public static final DeferredHolder<Block, HideLight> HIDE_LIGHT = BLOCKS.register("hide_light", () ->
            new HideLight(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NONE)
                            .noCollission()
                            .lightLevel(state -> state.getValue(HideLight.LEVEL))
                            .air()
                            .noLootTable()
            )
    );
}