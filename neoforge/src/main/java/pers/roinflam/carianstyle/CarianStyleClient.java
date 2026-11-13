package pers.roinflam.carianstyle;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import pers.roinflam.carianstyle.config.ClothConfigScreen;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 卡利亚式附魔的客户端入口：只在客户端加载，负责登记模组列表里的「配置」按钮。
 * <p>
 * <b>为什么单独一个类：</b>配置界面工厂会引用 {@code Screen} 等客户端类。写进主类的话，
 * 主类的常量池就带上了客户端类，专用服加载主类时有被连带解析的风险。
 * NeoForge 允许同一个 modid 有多个入口类，{@code dist = Dist.CLIENT} 的入口只在客户端实例化，
 * 专用服根本不会碰这个类。
 * </p>
 * <p>
 * 1.20.1 是在主类构造器里经 {@code ConfigScreenHandler.ConfigScreenFactory} 登记的，
 * 1.21.1 对应的扩展点是 {@link IConfigScreenFactory}，界面本身（{@link ClothConfigScreen}）不变。
 * </p>
 */
@Mod(value = Reference.MOD_ID, dist = Dist.CLIENT)
public class CarianStyleClient {

    public CarianStyleClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
                (modContainer, parent) -> ClothConfigScreen.createConfigScreen(parent));
    }
}
