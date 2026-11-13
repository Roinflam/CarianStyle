package pers.roinflam.carianstyle.mixin.accessor;

import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link SynchedEntityData#itemsById} 的访问器，给 {@code EntityLivingUtil} 找模组生物真正的血量同步字段用。
 * <p>
 * 1.20.1 按 SRG 名 {@code f_135345_} 反射，拿到的是 {@code Int2ObjectMap}；
 * 1.21.1 这个字段变成了按 id 下标的数组 {@code DataItem<?>[]}（private final），名字是 Mojang 名 {@code itemsById}。
 * </p>
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {

    @Accessor("itemsById")
    SynchedEntityData.DataItem<?>[] carianstyle$getItemsById();
}
