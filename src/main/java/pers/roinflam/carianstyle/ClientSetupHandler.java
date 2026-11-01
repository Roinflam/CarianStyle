package pers.roinflam.carianstyle;

import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMap;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.client.CarianStyleKeys;
import pers.roinflam.carianstyle.config.ClientVisualConfig;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.entity.render.GlintbladesRender;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.client.CarianStyleAuraDisplays;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;

/**
 * 客户端初始化：加载本机视觉配置并注册实体渲染、模型、按键和光环。
 * <p>
 * 1.12.2 版：不是事件订阅类，由主类在客户端的 preInit / init 里调用 {@link #preInit()} / {@link #init()}
 * （1.12.2 没有 MOD 总线，{@code FMLClientSetupEvent} 对应主类的生命周期）。
 * 火焰方块的 cutout 渲染层写在 {@code FireBase.getBlockLayer()}，这里不用设。
 * 模型注册在 {@link Models} 里订阅 {@code ModelRegistryEvent}。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ClientSetupHandler {

    /**
     * preInit 阶段：配置、实体渲染器
     */
    public static void preInit() {
        // 客户端启动即加载，避免必须先打开百科才能应用特效开关。
        ClientVisualConfig.load();
        VisualToggle.ensureLoaded();

        // 注册实体渲染器：工厂到加载实体渲染器时才调用，那时物品早已注册，get() 不会抛
        RenderingRegistry.registerEntityRenderingHandler(EntityGlintblades.class,
                manager -> new GlintbladesRender(manager, CarianStyleItem.GLINTBLADES.get()));
    }

    /**
     * init 阶段：按键、光环
     */
    public static void init() {
        CarianStyleKeys.register();

        // 注册光环显示探测器（客户端光环地面圈数据源；内置 5 个光环，装备对应附魔即显示）
        CarianStyleAuraDisplays.init();
    }

    /**
     * 物品模型与方块状态映射
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class Models {

        private Models() {
        }

        @SubscribeEvent
        public static void onModelRegister(ModelRegistryEvent event) {
            Item glintblades = CarianStyleItem.GLINTBLADES.get();
            ModelLoader.setCustomModelResourceLocation(glintblades, 0,
                    new ModelResourceLocation(glintblades.getRegistryName(), "inventory"));

            // 光源方块 16 个亮度状态都映射到同一个 normal 变体（空模型，只有粒子贴图）；
            // 不设的话每个状态都会报一条缺模型
            ModelLoader.setCustomStateMapper(CarianStyleBlocks.HIDE_LIGHT.get(),
                    new StateMap.Builder().ignore(HideLight.LEVEL).build());
        }
    }
}
