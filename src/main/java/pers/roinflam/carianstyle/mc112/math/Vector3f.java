package pers.roinflam.carianstyle.mc112.math;

/**
 * 三个 float 的向量，顶替 1.20.1 的 {@code org.joml.Vector3f}
 * <p>只当数据容器用（公开字段 x / y / z），需要运算的地方直接在调用处写。</p>
 */
public final class Vector3f {

    public float x;
    public float y;
    public float z;

    public Vector3f() {
    }

    public Vector3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3f set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ", " + z + ")";
    }
}
