package pers.roinflam.carianstyle.mc112.render;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.mc112.math.Matrix4f;

import javax.annotation.Nonnull;

/**
 * 顶点写入的包装，形状照 1.20.1 的 {@code com.mojang.blaze3d.vertex.BufferBuilder}
 * <p>
 * 1.20.1 的渲染器全是 {@code b.vertex(m, x, y, z).color(r, g, b, a).endVertex()}，辅助方法的参数是
 * {@code (BufferBuilder b, Matrix4f m, ...)}。1.12.2 版把 import 换成这个类和 {@code mc112.math.Matrix4f}，
 * 方法体基本不用动；内部转给 MC 的 {@code pos(...).color(...).endVertex()}。
 * </p>
 * <p>
 * 只有位置和颜色：1.20.1 用到贴图坐标、光照、法线的只有护鞘形状和自定义火焰两处，那两处直接写 MC 的缓冲。
 * 同一个文件里别再 import MC 的 {@code BufferBuilder}，要用就写全名。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class BufferBuilder {

    private final net.minecraft.client.renderer.BufferBuilder target;
    private final float[] scratch = new float[3];

    public BufferBuilder(@Nonnull net.minecraft.client.renderer.BufferBuilder target) {
        this.target = target;
    }

    /**
     * 写一个顶点的位置
     *
     * @param m 变换矩阵；恒等矩阵直接透传
     * @param x 坐标 x
     * @param y 坐标 y
     * @param z 坐标 z
     * @return this
     */
    @Nonnull
    public BufferBuilder vertex(@Nonnull Matrix4f m, float x, float y, float z) {
        if (m.isIdentity()) {
            target.pos(x, y, z);
        } else {
            m.transformPosition(x, y, z, scratch);
            target.pos(scratch[0], scratch[1], scratch[2]);
        }
        return this;
    }

    /**
     * 不带矩阵写位置（对应 1.20.1 的 {@code vertex(double, double, double)}）
     */
    @Nonnull
    public BufferBuilder vertex(double x, double y, double z) {
        target.pos(x, y, z);
        return this;
    }

    @Nonnull
    public BufferBuilder color(int r, int g, int b, int a) {
        target.color(r, g, b, a);
        return this;
    }

    @Nonnull
    public BufferBuilder color(float r, float g, float b, float a) {
        target.color(r, g, b, a);
        return this;
    }

    /** 这个顶点写完 */
    public void endVertex() {
        target.endVertex();
    }

    /** 底下 MC 的缓冲 */
    @Nonnull
    public net.minecraft.client.renderer.BufferBuilder unwrap() {
        return target;
    }
}
