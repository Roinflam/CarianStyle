package pers.roinflam.carianstyle.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.IModGuiFactory;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

/**
 * 模组列表里「配置」按钮打开的界面
 * <p>
 * 1.12.2 版：顶替 1.20.1 的 {@code ClothConfigScreen}，不再依赖 Cloth Config。
 * {@code GuiConfig(parent, modid, title)} 按 {@link ConfigSpec} 的 {@code @Config} 自动建分类页，
 * 分类名、选项名和提示都读 1.20.1 现成的 {@code config.carianstyle.*} 翻译。
 * 改完点「完成」时 Forge 发 {@code OnConfigChangedEvent}，由 {@link ConfigLoader#onConfigChanged} 同步并烘焙。
 * </p>
 */
@SideOnly(Side.CLIENT)
public class ConfigGuiFactory implements IModGuiFactory {

    @Override
    public void initialize(Minecraft minecraft) {
        // 无需初始化
    }

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Nonnull
    @Override
    public GuiScreen createConfigGui(GuiScreen parentScreen) {
        return new GuiConfig(parentScreen, Reference.MOD_ID, I18n.format("config.carianstyle.title"));
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return Collections.emptySet();
    }
}
