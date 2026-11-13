package pers.roinflam.carianstyle.fabric.mixin.event;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.LivingExperienceDropEvent;
import pers.roinflam.carianstyle.event.impl.LivingExperienceDropEventImpl;

/**
 * 末影龙死亡动画里分批给的经验：{@code tickDeath} 两处 {@code ExperienceOrb.award}（偏移 228 每 5 tick 一份、
 * 偏移 324 最后一份），各发一次经验事件改第 3 个参数，同 NeoForge 那两处 {@code getExperienceDrop}。
 * <p>
 * 攻击者取 {@code lastHurtByPlayer}。NeoForge 这里用的是它自己加的不过期字段，原版没有，
 * 打完很久才死透时后段可能拿不到攻击者；只影响按击杀者加成龙经验的附魔，接受这个差别。
 * 继承 {@code Mob} 只为读父类的受保护字段，构造器不会被调用。
 * </p>
 */
@Mixin(EnderDragon.class)
public abstract class FabricEnderDragonMixin extends Mob {

    protected FabricEnderDragonMixin(EntityType<? extends Mob> type, Level level) {
        super(type, level);
    }

    @ModifyArg(method = "tickDeath",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V",
                    ordinal = 0),
            index = 2)
    private int carianstyle$dragonExperience(int experience) {
        return carianstyle$post(experience);
    }

    @ModifyArg(method = "tickDeath",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V",
                    ordinal = 1),
            index = 2)
    private int carianstyle$dragonFinalExperience(int experience) {
        return carianstyle$post(experience);
    }

    private int carianstyle$post(int experience) {
        LivingExperienceDropEvent event = CarianBus.post(
                new LivingExperienceDropEventImpl((LivingEntity) this, lastHurtByPlayer, experience));
        return event.getDroppedExperience();
    }
}
