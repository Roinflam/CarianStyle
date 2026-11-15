package pers.roinflam.carianstyle.mixin.accessor;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link SynchedEntityData#itemsById} 的访问器，给 {@code EntityLivingUtil} 找模组生物真正的血量同步字段用。
 * <p>
 * 以前按 SRG 名 {@code f_135345_} 反射，只在 Forge 上成立。1.20.1 这个字段是 {@code Int2ObjectMap}
 * （1.21.1 换成了按 id 下标的数组，两个版本这里不一样）。
 * </p>
 */
@Mixin(SynchedEntityData.class)
public interface SynchedEntityDataAccessor {

    @Accessor("itemsById")
    Int2ObjectMap<SynchedEntityData.DataItem<?>> carianstyle$getItemsById();
}
