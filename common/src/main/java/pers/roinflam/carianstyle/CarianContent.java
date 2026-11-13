package pers.roinflam.carianstyle;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import pers.roinflam.carianstyle.init.CarianStyleBlockEntities;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEntity;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.platform.Services;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 本模组的注册内容（方块、方块实体、实体、物品、效果，共 17 项）
 * <p>
 * {@code init/} 下 5 个类的静态字段就是声明，经 {@link #register} / {@link #registerHolder} 交给平台的注册实现：
 * 有的平台先记下、到注册事件里统一注册（返回的对象这时还取不到值），有的平台当场注册。
 * 不管哪种，声明都必须在平台的注册时机之前做完，所以入口要显式调 {@link #bootstrap()}，
 * 不能靠「第一次用到某个字段时类才加载」——那时注册可能早就过去了。
 * </p>
 * <p>
 * 触发顺序固定为方块 → 方块实体 → 实体 → 物品 → 效果。同一个注册表里的条目按字段声明顺序注册；
 * 存档按字符串 id 存，数字 id 由服务端下发，这个顺序不影响存档和联机，只是保持不变省得排查时多一个变量。
 * </p>
 */
public final class CarianContent {

    /** 每个注册表声明了几项（日志和加载完成后的自检用） */
    private static final Map<ResourceKey<?>, Integer> DECLARED = new HashMap<>();

    private static boolean bootstrapped;

    private CarianContent() {
    }

    /**
     * 触发 {@code init/} 下 5 个类的静态初始化，把全部内容声明给平台。只许调一次。
     */
    public static synchronized void bootstrap() {
        if (bootstrapped) {
            throw new IllegalStateException("注册内容已经声明过了");
        }
        bootstrapped = true;

        touch(CarianStyleBlocks.class);
        LogUtil.info("卡利亚式附魔 - 方块已声明：%d 项", declaredCount(Registries.BLOCK));

        touch(CarianStyleBlockEntities.class);
        LogUtil.info("卡利亚式附魔 - 方块实体已声明：%d 项", declaredCount(Registries.BLOCK_ENTITY_TYPE));

        touch(CarianStyleEntity.class);
        LogUtil.info("卡利亚式附魔 - 实体类型已声明：%d 项", declaredCount(Registries.ENTITY_TYPE));

        touch(CarianStyleItem.class);
        LogUtil.info("卡利亚式附魔 - 物品已声明：%d 项", declaredCount(Registries.ITEM));

        touch(CarianStylePotion.class);
        LogUtil.info("卡利亚式附魔 - 药水效果已声明：%d 项", declaredCount(Registries.MOB_EFFECT));
    }

    /**
     * 声明一项内容，返回取值用的 {@link Supplier}（注册完成后才能 {@code get()}）。
     */
    public static <R, T extends R> Supplier<T> register(ResourceKey<? extends Registry<R>> registry, String path,
                                                       Supplier<T> factory) {
        count(registry);
        return Services.registry().register(registry, path, factory);
    }

    /**
     * 声明一项内容，返回注册表里的 {@link Holder}（效果这类要按 Holder 用的）。
     */
    public static <R, T extends R> Holder<R> registerHolder(ResourceKey<? extends Registry<R>> registry, String path,
                                                           Supplier<T> factory) {
        count(registry);
        return Services.registry().registerHolder(registry, path, factory);
    }

    /**
     * 某个注册表里本模组声明了几项。
     */
    public static synchronized int declaredCount(ResourceKey<? extends Registry<?>> registry) {
        return DECLARED.getOrDefault(registry, 0);
    }

    /**
     * 加载完成后的自检：效果、实体一项都没有说明声明没走到，记错误日志并返回 false。
     */
    public static boolean validate() {
        boolean valid = true;
        if (declaredCount(Registries.MOB_EFFECT) == 0) {
            LogUtil.error("卡利亚式附魔 - 错误：没有药水效果被注册");
            valid = false;
        }
        if (declaredCount(Registries.ENTITY_TYPE) == 0) {
            LogUtil.error("卡利亚式附魔 - 错误：没有实体被注册");
            valid = false;
        }
        return valid;
    }

    private static synchronized void count(ResourceKey<?> registry) {
        DECLARED.merge(registry, 1, Integer::sum);
    }

    private static void touch(Class<?> holderClass) {
        try {
            Class.forName(holderClass.getName(), true, holderClass.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("加载注册类失败：" + holderClass.getName(), e);
        }
    }
}
