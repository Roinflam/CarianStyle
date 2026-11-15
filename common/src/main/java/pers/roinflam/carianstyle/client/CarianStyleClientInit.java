package pers.roinflam.carianstyle.client;

import pers.roinflam.carianstyle.config.ClientVisualConfig;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.visual.client.CarianStyleAuraDisplays;
import pers.roinflam.carianstyle.visual.client.VisualRangeCheck;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;

import javax.annotation.Nonnull;

/**
 * 客户端初始化：加载本机视觉配置，登记实体渲染器、方块渲染层和光环显示。
 * <p>
 * 各加载器的客户端入口在客户端初始化阶段调一次 {@link #setup}，要在主线程上调。
 * 按键和 HUD 层不在这里，加载器要求的登记时机更早，由入口另外经 {@link ClientContent} 交出去。
 * </p>
 */
public final class CarianStyleClientInit {

    private static boolean done;

    private CarianStyleClientInit() {
    }

    /**
     * 客户端初始化。只许调一次。
     *
     * @param platform 客户端平台实现
     */
    public static void setup(@Nonnull ClientPlatform platform) {
        if (done) {
            throw new IllegalStateException("客户端初始化已经做过了");
        }
        done = true;

        // 客户端启动即加载，避免必须先打开百科才能应用特效开关。
        ClientVisualConfig.load();
        VisualToggle.ensureLoaded();

        // 实体渲染器、火焰方块的渲染层
        ClientContent.registerEntityRenderers(platform);
        ClientContent.registerBlockRenderLayers(platform);

        // 注册光环显示探测器（客户端光环地面圈数据源；内置 5 个光环，装备对应附魔即显示）
        CarianStyleAuraDisplays.init();

        // 视觉范围契约自检（纯防呆，只读常量 + 记日志，不影响任何渲染行为）：
        // 校验各渲染器的裁剪距离没有超过其数据来源（共享查询 / 光环扫描）的范围上限，
        // 避免「改了某个 CULL 之后远距离特效静默消失」这类极难排查的问题。
        VisualRangeCheck.validate();
    }
}
