package pers.roinflam.carianstyle.mc112.math;

/**
 * 最小版 4×4 矩阵，顶替 1.20.1 渲染代码里的 {@code org.joml.Matrix4f}
 * <p>
 * 1.12.2 没有 JOML。1.20.1 的渲染器都是 {@code b.vertex(m, x, y, z)} 这种写法，矩阵来自
 * {@code PoseStack.last().pose()}；1.12.2 版在 {@code RenderWorldLastEvent} 里画，模型视图矩阵已经带着相机变换，
 * 顶点直接写相对渲染原点的坐标就行，所以实际只用到 {@link #IDENTITY}。
 * 这里仍留着一般矩阵的乘点，免得以后有渲染器真的需要非恒等变换时还要改接口。
 * </p>
 * <p>元素按行存：{@code m[row * 4 + col]}，点按列向量右乘。</p>
 */
public final class Matrix4f {

    /** 恒等矩阵单例。不要改它的元素 */
    public static final Matrix4f IDENTITY = new Matrix4f();

    private final float[] m = new float[16];
    private final boolean identity;

    /** 新建一个恒等矩阵 */
    public Matrix4f() {
        m[0] = 1.0F;
        m[5] = 1.0F;
        m[10] = 1.0F;
        m[15] = 1.0F;
        identity = true;
    }

    /**
     * 按行给出 16 个元素
     *
     * @param values 行优先的 16 个元素
     */
    public Matrix4f(float[] values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("矩阵要 16 个元素，实际 " + values.length);
        }
        System.arraycopy(values, 0, m, 0, 16);
        boolean id = true;
        for (int i = 0; i < 16; i++) {
            float expect = (i % 5 == 0) ? 1.0F : 0.0F;
            if (m[i] != expect) {
                id = false;
                break;
            }
        }
        identity = id;
    }

    /**
     * 是不是恒等矩阵（顶点包装遇到恒等矩阵直接透传，不做乘法）
     */
    public boolean isIdentity() {
        return identity;
    }

    /**
     * 变换一个点（w = 1），结果写进 {@code out}
     *
     * @param x   点的 x
     * @param y   点的 y
     * @param z   点的 z
     * @param out 长度 3 的输出数组
     */
    public void transformPosition(float x, float y, float z, float[] out) {
        if (identity) {
            out[0] = x;
            out[1] = y;
            out[2] = z;
            return;
        }
        float rx = m[0] * x + m[1] * y + m[2] * z + m[3];
        float ry = m[4] * x + m[5] * y + m[6] * z + m[7];
        float rz = m[8] * x + m[9] * y + m[10] * z + m[11];
        float rw = m[12] * x + m[13] * y + m[14] * z + m[15];
        if (rw != 1.0F && rw != 0.0F) {
            rx /= rw;
            ry /= rw;
            rz /= rw;
        }
        out[0] = rx;
        out[1] = ry;
        out[2] = rz;
    }
}
