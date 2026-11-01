package pers.roinflam.carianstyle.init;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.mc112.registry.DeferredRegister;
import pers.roinflam.carianstyle.mc112.registry.RegistryObject;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组实体注册类
 * <p>
 * 使用 DeferredRegister 方式注册所有自定义实体
 * </p>
 * <p>
 * 1.12.2 版：注册的是 {@code EntityEntry}。几个参数和 1.20.1 的对应关系：
 * </p>
 * <ul>
 *   <li>追踪距离：1.20.1 的 {@code clientTrackingRange(64)} 单位是区块；1.12.2 的单位是格，
 *       而且会被服务端视距封顶，所以写 1024（= 64 区块），效果一样是「视距内都同步」；</li>
 *   <li>更新间隔 10 照 1.20.1；</li>
 *   <li>尺寸 0.75 × 0.75 写在实体构造器里；</li>
 *   <li>网络编号从 0 起按注册顺序。</li>
 * </ul>
 * <p>
 * 构建器在类加载时（preInit，本模组是当前活动模组）就建好，注册事件里只调 {@code build()}：
 * {@code EntityEntryBuilder.create()} 会记下当时的活动模组，网络生成实体时靠它找到所属模组。
 * </p>
 */
public class CarianStyleEntity {

    /**
     * 实体延迟注册器
     */
    public static final DeferredRegister<EntityEntry> ENTITY_TYPES =
            DeferredRegister.create(EntityEntry.class, Reference.MOD_ID);

    private static final EntityEntryBuilder<EntityGlintblades> GLINTBLADES_BUILDER = EntityEntryBuilder
            .<EntityGlintblades>create()
            .entity(EntityGlintblades.class)
            .id(new ResourceLocation(Reference.MOD_ID, "glintblades"), 0)
            .name(Reference.MOD_ID + ".glintblades")
            .tracker(1024, 10, true);

    /**
     * 魔法辉剑实体
     */
    public static final RegistryObject<EntityEntry> GLINTBLADES =
            ENTITY_TYPES.register("glintblades", GLINTBLADES_BUILDER::build);
}
