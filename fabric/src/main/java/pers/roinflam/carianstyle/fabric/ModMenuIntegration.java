package pers.roinflam.carianstyle.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import pers.roinflam.carianstyle.config.ClothConfigScreen;

/**
 * Mod Menu 的模组列表里「配置」按钮的入口（{@code fabric.mod.json} 的 {@code modmenu}）。
 * Mod Menu 是可选的：没装它时这个类不会被加载。
 * 打开的界面和 NeoForge 模组列表里的「配置」按钮是同一个（{@link ClothConfigScreen}）。
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClothConfigScreen::createConfigScreen;
    }
}
