package pers.roinflam.carianstyle.platform;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import java.util.function.Supplier;

/**
 * 游戏内容（方块、物品、实体、药水效果、方块实体）的注册。
 * <p>
 * 两个加载器注册的时机不一样：NeoForge 只能先声明、等注册事件再真正建对象（返回的是延迟持有者，
 * 注册事件之前 {@code get()} 会抛异常）；Fabric 当场就注册。所以返回值只保证「注册阶段结束后可用」，
 * 声明时不要去取值。
 * </p>
 * <p>
 * 同一个注册表、同一个路径只能注册一次；注册阶段结束后再调会抛异常。
 * </p>
 */
public interface PlatformRegistry {

    /**
     * 声明一项内容。
     *
     * @param registry 注册表键，如 {@code Registries.BLOCK}
     * @param path     本模组命名空间下的路径
     * @param factory  建对象的方法，由平台在注册时调用一次
     * @return 注册后可以取到对象的持有者
     */
    <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path,
                                          Supplier<T> factory);

    /**
     * 同 {@link #register}，但要的是注册表持有者（药水效果这类要 {@code Holder} 才能用的）。
     */
    <R, T extends R> Holder<R> registerHolder(ResourceKey<? extends Registry<R>> registry, String path,
                                             Supplier<T> factory);
}
