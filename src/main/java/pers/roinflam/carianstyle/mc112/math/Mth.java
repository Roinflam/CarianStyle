package pers.roinflam.carianstyle.mc112.math;

import net.minecraft.util.math.MathHelper;

/**
 * 1.20.1 {@code net.minecraft.util.Mth} 的 1.12.2 对应
 * <p>
 * 1.12.2 的 {@code MathHelper} 方法名不同、也没有 long 版的 clamp。从 1.20.1 搬来的代码统一用这里的，
 * 方法名和参数顺序照 1.20.1：{@code lerp(delta, start, end)}、{@code clamp(value, min, max)}。
 * 三角函数转给 {@code MathHelper} 的查表实现，和 1.20.1 {@code Mth.sin / cos} 同样是查表，精度一致。
 * </p>
 */
public final class Mth {

    /** π，float */
    public static final float PI = (float) Math.PI;
    /** 角度转弧度 */
    public static final float DEG_TO_RAD = PI / 180.0F;
    /** 弧度转角度 */
    public static final float RAD_TO_DEG = 180.0F / PI;

    private Mth() {
    }

    public static float sin(float value) {
        return MathHelper.sin(value);
    }

    public static float cos(float value) {
        return MathHelper.cos(value);
    }

    public static float sqrt(float value) {
        return MathHelper.sqrt(value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    public static long clamp(long value, long min, long max) {
        return value < min ? min : Math.min(value, max);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    /**
     * 向下取整（负数也往小的方向取）
     */
    public static int floor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int floor(float value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    public static int ceil(double value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static int ceil(float value) {
        int i = (int) value;
        return value > i ? i + 1 : i;
    }

    public static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    public static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    /**
     * 角度插值：走最短的那一边（跨过 ±180° 时不绕远路），和 1.20.1 {@code Mth.rotLerp} 一样
     */
    public static float rotLerp(float delta, float start, float end) {
        return start + delta * wrapDegrees(end - start);
    }

    /**
     * 把角度规整到 [-180, 180)
     */
    public static float wrapDegrees(float degrees) {
        return MathHelper.wrapDegrees(degrees);
    }

    /**
     * 小数部分（0 ≤ 结果 &lt; 1）
     */
    public static double frac(double value) {
        return value - Math.floor(value);
    }

    /**
     * 整数除法向下取整（被除数为负时也往小的方向）
     */
    public static int floorDiv(int dividend, int divisor) {
        return Math.floorDiv(dividend, divisor);
    }

    public static int positiveModulo(int value, int modulus) {
        return Math.floorMod(value, modulus);
    }
}
