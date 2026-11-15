package pers.roinflam.carianstyle.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.core.registries.Registries;
import pers.roinflam.carianstyle.CarianContent;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import java.util.function.Supplier;

/**
 * 模组实体注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义实体
 * </p>
 */
public class CarianStyleEntity {

    /**
     * 魔法辉剑实体类型
     */
    public static final Supplier<EntityType<EntityGlintblades>> GLINTBLADES =
            CarianContent.register(Registries.ENTITY_TYPE, "glintblades", () ->
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