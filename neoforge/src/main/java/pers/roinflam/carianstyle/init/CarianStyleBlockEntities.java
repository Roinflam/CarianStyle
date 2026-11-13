package pers.roinflam.carianstyle.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.CarianContent;
import net.minecraft.core.registries.Registries;

import java.util.function.Supplier;

/**
 * 模组方块实体注册类
 * <p>
 * 声明交给 {@link CarianContent}，由它在模组加载时触发本类、交给平台注册；字段在注册完成后才能 {@code get()}。
 * </p>
 */
public class CarianStyleBlockEntities {

    /**
     * 移动光源方块实体类型
     */
    public static final Supplier<BlockEntityType<MoveLight>> MOVE_LIGHT =
            CarianContent.register(Registries.BLOCK_ENTITY_TYPE, "move_light", () ->
                    BlockEntityType.Builder.of(
                            MoveLight::new,
                            CarianStyleBlocks.HIDE_LIGHT.get()
                    ).build(null)
            );
}