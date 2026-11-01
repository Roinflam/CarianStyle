package pers.roinflam.carianstyle.mc112.util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 反射小工具：先按 MCP 名找、找不到再按 SRG 名找
 * <p>
 * 开发环境（gradle runClient / runServer）里原版的字段、方法是 MCP 名，打出来的 jar 在正式环境里是 SRG 名，
 * 两个名字都给，哪个找得到用哪个。找到后 {@code setAccessible(true)}。调用方把结果缓存成静态字段，
 * 不要每次都查。
 * </p>
 * <p>Forge 自己的类、本模组的类不混淆，两个名字传一样的就行。</p>
 */
public final class Reflect {

    private Reflect() {
    }

    /**
     * 找字段，找不到抛异常
     *
     * @param owner   声明这个字段的类（不查父类）
     * @param mcpName 开发环境的名字
     * @param srgName 正式环境的名字
     * @return 已设为可访问的字段
     * @throws IllegalStateException 两个名字都找不到
     */
    @Nonnull
    public static Field field(@Nonnull Class<?> owner, @Nonnull String mcpName, @Nonnull String srgName) {
        Field f = fieldOrNull(owner, mcpName, srgName);
        if (f == null) {
            throw new IllegalStateException("找不到字段 " + owner.getName() + "." + mcpName + " / " + srgName);
        }
        return f;
    }

    /**
     * 找字段，找不到返回 null
     *
     * @param owner   声明这个字段的类（不查父类）
     * @param mcpName 开发环境的名字
     * @param srgName 正式环境的名字
     * @return 已设为可访问的字段，找不到为 null
     */
    @Nullable
    public static Field fieldOrNull(@Nonnull Class<?> owner, @Nonnull String mcpName, @Nonnull String srgName) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
                // 换另一个名字
            } catch (SecurityException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 找方法，找不到抛异常
     *
     * @param owner   声明这个方法的类（不查父类）
     * @param mcpName 开发环境的名字
     * @param srgName 正式环境的名字
     * @param params  参数类型
     * @return 已设为可访问的方法
     * @throws IllegalStateException 两个名字都找不到
     */
    @Nonnull
    public static Method method(@Nonnull Class<?> owner, @Nonnull String mcpName, @Nonnull String srgName,
                                @Nonnull Class<?>... params) {
        for (String name : new String[]{mcpName, srgName}) {
            try {
                Method m = owner.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                // 换另一个名字
            }
        }
        throw new IllegalStateException("找不到方法 " + owner.getName() + "." + mcpName + " / " + srgName);
    }
}
