package pers.roinflam.carianstyle.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.Reference;
import net.minecraft.core.registries.Registries;

/**
 * 模组实体注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义实体
 * </p>
 */
public class CarianStyleEntity {

    /**
     * 实体类型延迟注册器
     */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Reference.MOD_ID);

    /**
     * 魔法辉剑实体类型
     */
    public static final DeferredHolder<EntityType<?>, EntityType<EntityGlintblades>> GLINTBLADES =
            ENTITY_TYPES.register("glintblades", () ->
                    EntityType.Builder.<EntityGlintblades>of(
                                    EntityGlintblades::new,
                                    MobCategory.MISC
                            )
                            .sized(0.75F, 0.75F)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .build("glintblades")
            );
}