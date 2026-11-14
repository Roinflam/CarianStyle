package pers.roinflam.carianstyle.mixin.accessor;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * {@link LivingEntity} 私有成员的访问器，给 {@code utils/ReflectionCache} 用。
 * <p>
 * 以前按 SRG 名反射（{@code f_20954_}、{@code f_20936_}、{@code f_20961_}、{@code m_21329_}），只有 Forge 运行期认这套名字，
 * Fabric 运行期是 intermediary 名，反射全部落空。这里写 Mojang 名，构建时随 jar 一起静态重映射成各平台的运行期名字
 * （没有 refmap），名字写错在挂载时就报错，不会像反射那样悄悄失效。
 * 目标成员（1.20.1 {@code LivingEntity}）：
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
