package pers.roinflam.carianstyle.mc112.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 一套固定的 GL 状态，顶替 1.20.1 的 {@code RenderType}
 * <p>{@link #enter()} 先记下当前的真实状态，再设成这套预设；画完调返回值的 {@link GlStateSnapshot#restore()} 还原。</p>
 */
@SideOnly(Side.CLIENT)
public interface GlPreset {

    /**
     * 记下当前状态、设预设
     *
     * @return 进入前的状态，画完用它还原
     */
    GlStateSnapshot enter();
}
