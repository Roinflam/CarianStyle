package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 叠层 HUD 覆盖层注册（客户端）。
 * <p>
 * 1.20.1 在这里订阅 MOD 总线的 {@code RegisterGuiOverlaysEvent}，把 {@link StackHudOverlay#INSTANCE}
 * 注册成最上层的覆盖层。
 * </p>
 * <p>
 * 1.12.2 版：没有覆盖层注册这一步，HUD 由 {@link StackHudOverlay} 自己订阅
 * {@code RenderGameOverlayEvent.Post}（只在 {@code ElementType.ALL} 时画）。
 * 这个类只剩一个空壳，保留同名是为了两边按文件对照时能一一对上。
 * </p>
 *
 * @author RoinFlam
 */
@SideOnly(Side.CLIENT)
public final class StackHudRegister {

    private StackHudRegister() {
    }
}
