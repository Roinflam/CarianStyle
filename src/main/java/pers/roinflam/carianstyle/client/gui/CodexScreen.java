package pers.roinflam.carianstyle.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.gui.Component;
import pers.roinflam.carianstyle.mc112.gui.Screen;

/**
 * PORT-STUB(G2)
 * <p>附魔百科界面：桩是一个空白界面，K 键能打开、Esc 能关。</p>
 */
@Deprecated
@SideOnly(Side.CLIENT)
public class CodexScreen extends Screen {

    public CodexScreen() {
        super(Component.translatable("carianstyle.codex.title"));
    }
}
