package pers.roinflam.carianstyle.init;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.CarianContent;
import net.minecraft.core.registries.Registries;

import java.util.function.Supplier;

/**
 * 模组实体注册类
 * <p>
 * 声明交给 {@link CarianContent}，由它在模组加载时触发本类、交给平台注册；字段在注册完成后才能 {@code get()}。
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