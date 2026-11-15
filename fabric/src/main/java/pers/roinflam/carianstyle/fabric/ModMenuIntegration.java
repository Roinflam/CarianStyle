package pers.roinflam.carianstyle.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import pers.roinflam.carianstyle.config.ClothConfigScreen;

// Mod Menu 是可选依赖，没装时这个类不会被加载；打开的和 Forge 模组列表里「配置」按钮是同一个界面
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ClothConfigScreen::createConfigScreen;
    }
}
