package pers.roinflam.carianstyle.mixin.accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import org.jetbrains.annotations.Nullable;

/**
 * {@link DamageSource} 四参数构造器（类型、直接来源、间接来源、位置）的调用器，给 {@code DamageOverTimeManager}
 * 复制伤害源用。
 * <p>
 * 原版里这个构造器是 private，NeoForge / Forge 靠加载器自带的访问转换器把它放成 public，模组直接 {@code new} 就行；
 * Fabric 上没有这条访问转换，直接 {@code new} 编译不过。改用 {@code @Invoker("<init>")}，两个加载器同一写法，
 * 生成的就是对同一个构造器的调用，结果和直接 {@code new} 完全一样。
 * </p>
 */
@Mixin(DamageSource.class)
public interface DamageSourceInvoker {

    @Invoker("<init>")
    static DamageSource carianstyle$create(Holder<DamageType> type, @Nullable Entity directEntity,
                                           @Nullable Entity causingEntity, @Nullable Vec3 sourcePosition) {
        throw new AssertionError();
    }
}
