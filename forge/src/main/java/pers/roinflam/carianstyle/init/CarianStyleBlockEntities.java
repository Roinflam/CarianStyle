package pers.roinflam.carianstyle.init;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组方块实体注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有方块实体类型
 * </p>
 */
public class CarianStyleBlockEntities {

    /**
     * 方块实体延迟注册器
     */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Reference.MOD_ID);

    /**
     * 移动光源方块实体类型
     */
    public static final RegistryObject<BlockEntityType<MoveLight>> MOVE_LIGHT =
            BLOCK_ENTITY_TYPES.register("move_light", () ->
                    BlockEntityType.Builder.of(
                            MoveLight::new,
                            CarianStyleBlocks.HIDE_LIGHT.get()
                    ).build(null)
            );
}