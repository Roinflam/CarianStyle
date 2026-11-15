package pers.roinflam.carianstyle.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.registries.Registries;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.tileentity.MoveLight;

import java.util.function.Supplier;

/**
 * 模组方块实体注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有方块实体类型
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