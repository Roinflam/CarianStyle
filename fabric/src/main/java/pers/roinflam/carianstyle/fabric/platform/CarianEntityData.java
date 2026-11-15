package pers.roinflam.carianstyle.fabric.platform;

import net.minecraft.nbt.CompoundTag;

/**
 * 由 {@code mixin/entity/FabricEntityPersistentDataMixin} 加到每个 {@code Entity} 上：跟着存档走的自定义数据，
 * 对应 Forge 的 {@code Entity#getPersistentData}。
 * <p>
 * 存在实体 NBT 的 {@code CarianStyleData} 键下，和 Forge 的 {@code ForgeData} 不互通：
 * 同一个存档换加载器打开，本模组记在实体上的标记会当作没有。
 * </p>
 */
public interface CarianEntityData {

    /** 实体上的自定义数据，没有时当场建一个空的；不会返回 null。 */
    CompoundTag carianstyle$getPersistentData();
}
