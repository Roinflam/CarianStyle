package pers.roinflam.carianstyle.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link LivingEntity} 私有成员的访问器，给 {@code utils/ReflectionCache} 用。
 * <p>
 * 1.20.1 是按 SRG 名反射（{@code f_20954_}、{@code f_20936_}、{@code f_20961_}、{@code m_21329_}）。
 * 1.21.1 运行期是 Mojang 映射、没有 SRG 名，这里直接写 Mojang 名；本模组的 mixin 配置没有 refmap，
 * 名字原样匹配运行期字节码，写错了挂载时就会报错，不会像反射那样悄悄失效。
 * 目标成员（1.21.1 {@code LivingEntity}）：
 * {@code private int noJumpDelay}、{@code protected int useItemRemaining}、
 * {@code private static final EntityDataAccessor<Float> DATA_HEALTH_ID}、{@code private void updatingUsingItem()}。
 * </p>
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {

    @Accessor("noJumpDelay")
    int carianstyle$getNoJumpDelay();

    @Accessor("noJumpDelay")
    void carianstyle$setNoJumpDelay(int ticks);

    @Accessor("useItemRemaining")
    int carianstyle$getUseItemRemaining();

    @Accessor("useItemRemaining")
    void carianstyle$setUseItemRemaining(int ticks);

    @Accessor("DATA_HEALTH_ID")
    static EntityDataAccessor<Float> carianstyle$getDataHealthId() {
        throw new AssertionError();
    }

    @Invoker("updatingUsingItem")
    void carianstyle$updatingUsingItem();
}
