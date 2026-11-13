package pers.roinflam.carianstyle.init;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import pers.roinflam.carianstyle.block.fire.CrimsonFlame;
import pers.roinflam.carianstyle.block.fire.WhiteFlame;
import pers.roinflam.carianstyle.block.fire.YellowFlame;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.CarianContent;
import net.minecraft.core.registries.Registries;

import java.util.function.Supplier;

/**
 * 模组方块注册类
 * <p>
 * 声明交给 {@link CarianContent}，由它在模组加载时触发本类、交给平台注册；字段在注册完成后才能 {@code get()}。
 * </p>
 */
public class CarianStyleBlocks {

    /**
     * 猩红火焰方块
     */
    public static final Supplier<CrimsonFlame> CRIMSON_FLAME = CarianContent.register(Registries.BLOCK, "crimson_flame", () ->
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
    public static final Supplier<WhiteFlame> WHITE_FLAME = CarianContent.register(Registries.BLOCK, "white_flame", () ->
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
    public static final Supplier<YellowFlame> YELLOW_FLAME = CarianContent.register(Registries.BLOCK, "yellow_flame", () ->
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
    public static final Supplier<HideLight> HIDE_LIGHT = CarianContent.register(Registries.BLOCK, "hide_light", () ->
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