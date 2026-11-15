package pers.roinflam.carianstyle.fabric.mixin.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.fabric.platform.CarianEntityData;

/**
 * 给每个实体加一份跟着存档走的自定义数据（{@link CarianEntityData}），写法同 Forge 的 {@code getPersistentData}：
 * 用到时才建；建过就随实体 NBT 存读（键 {@code CarianStyleData}），读档时存档里没有这个键就保留现有的。
 * <p>
 * 实体换维度时原版按「存一份 NBT、新实体读进来」复制，数据跟着走；玩家死亡重生、从末地回来时新玩家对象不读旧玩家的 NBT，
 * 数据不跟着走，要保留的由本模组的换身复制监听自己拷（同 Forge）。
 * </p>
 */
@Mixin(Entity.class)
public abstract class FabricEntityPersistentDataMixin implements CarianEntityData {

    @Unique
    private static final String CARIANSTYLE_DATA_KEY = "CarianStyleData";

    @Unique
    private CompoundTag carianstyle$persistentData;

    @Override
    public CompoundTag carianstyle$getPersistentData() {
        if (carianstyle$persistentData == null) {
            carianstyle$persistentData = new CompoundTag();
        }
        return carianstyle$persistentData;
    }

    @Inject(method = "saveWithoutId", at = @At("HEAD"))
    private void carianstyle$savePersistentData(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (carianstyle$persistentData != null) {
            tag.put(CARIANSTYLE_DATA_KEY, carianstyle$persistentData.copy());
        }
    }

    @Inject(method = "load", at = @At("HEAD"))
    private void carianstyle$loadPersistentData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains(CARIANSTYLE_DATA_KEY, Tag.TAG_COMPOUND)) {
            carianstyle$persistentData = tag.getCompound(CARIANSTYLE_DATA_KEY);
        }
    }
}
