package pers.roinflam.carianstyle.mc112.registry;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * 已登记、等注册事件填值的注册项，对应 1.20.1 的 {@code net.minecraftforge.registries.RegistryObject}
 * <p>
 * {@link DeferredRegister#register(String, Supplier)} 返回它，注册事件里实例化后才有值。
 * 和 1.20.1 一样，注册之前调 {@link #get()} 会抛 {@link IllegalStateException}，
 * 这样「类加载时就去拿方块 / 物品」的写法在开发环境里一跑就暴露。
 * </p>
 *
 * @param <T> 注册项类型
 */
public final class RegistryObject<T> implements Supplier<T> {

    private final ResourceLocation id;
    @Nullable
    private T value;

    RegistryObject(@Nonnull ResourceLocation id) {
        this.id = id;
    }

    /**
     * 取注册好的实例
     *
     * @return 实例
     * @throws IllegalStateException 注册事件还没到
     */
    @Nonnull
    @Override
    public T get() {
        T v = value;
        if (v == null) {
            throw new IllegalStateException("注册项还没注册：" + id);
        }
        return v;
    }

    /**
     * 注册名（{@code carianstyle:<名字>}）
     */
    @Nonnull
    public ResourceLocation getId() {
        return id;
    }

    /**
     * 是否已经注册
     */
    public boolean isPresent() {
        return value != null;
    }

    void set(@Nonnull T value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return "RegistryObject{" + id + "}";
    }
}
