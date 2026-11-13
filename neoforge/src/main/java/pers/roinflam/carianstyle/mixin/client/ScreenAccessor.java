package pers.roinflam.carianstyle.mixin.client;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 读 {@link Screen} 私有的控件列表。
 * <p>
 * 百科界面自己画背景，不调 {@code super.render}，控件要自己逐个画，所以得拿到这张表。
 * NeoForge 靠访问转换器把字段放开了，别的加载器没有，统一走这个访问器。
 * </p>
 */
@Mixin(Screen.class)
public interface ScreenAccessor {

    @Accessor("renderables")
    List<Renderable> carianstyle$getRenderables();
}
