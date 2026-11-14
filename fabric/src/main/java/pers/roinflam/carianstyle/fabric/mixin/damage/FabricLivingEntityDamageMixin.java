package pers.roinflam.carianstyle.fabric.mixin.damage;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.base.entity.AbsorptionShield;
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;

/**
 * 非玩家生物的伤害三段（攻击 / 受伤 / 伤害），发到模组自带总线。玩家走 {@link FabricPlayerDamageMixin}
 * （{@code Player.actuallyHurt} 不调 super）。
 * <p>
 * 原版 {@code actuallyHurt} 的局部变量：槽 2 = 参数 amount（反复重赋值），槽 3 = 护甲和魔法减免后的值，
 * 槽 4 = 被吸收掉的量（只用于统计）。按类型数 float 的序号时参数也算在内：槽 2 = 0、槽 3 = 1、槽 4 = 2。
 * 原版没有 NeoForge 的伤害容器栈，嵌套 {@code hurt} 天然互不影响。
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class FabricLivingEntityDamageMixin {

    /**
     * 攻击阶段：{@code hurt} 第一行，取消 = 返回 false（不进无敌时间、不扣血）。
     * 玩家由 {@code Player.hurt} 那份发，这里跳过免得发两次（Forge 的 {@code onLivingAttack} 同样跳过玩家）。
     */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$attackStage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player) {
            return;
        }
        if (CarianBus.post(new LivingAttackEvent(self, source, amount)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 受伤阶段：{@code actuallyHurt} 第一行，先照原版判 {@code isInvulnerableTo}（为真就什么都不做，交给原版那行跳过），
     * 再发事件改参数 amount。取消或结果 ≤ 0 直接返回，和 Forge 1.20.1 的 {@code onLivingHurt} 同位置同口径；
     * 这时 {@code hurt} 里只有无敌时间已经写下；受击动画、击退、死亡判定都在本段之后，取消本段不影响它们（和 NeoForge 一样）。
     * <p>
     * 不能挂在 {@code getDamageAfterArmorAbsorb} 调用前：那时参数已经压栈，改槽 2 对这次调用不生效。
     * </p>
     */
    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$hurtStage(DamageSource source, float ignored, CallbackInfo ci,
                                       @Local(argsOnly = true) LocalFloatRef amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isInvulnerableTo(source)) {
            return;
        }
        LivingHurtEvent event = CarianBus.post(new LivingHurtEvent(self, source, amount.get()));
        if (event.isCanceled() || event.getAmount() <= 0.0F) {
            ci.cancel();
            return;
        }
        amount.set(event.getAmount());
    }

    /**
     * 伤害阶段：借槽 4（被吸收量）存完的位置发，处理器原样返回槽 4，改的是参数槽 2。
     * <p>
     * 这时第一次 {@code setAbsorptionAmount}（偏移 45）已经扣过吸收、统计 {@code DAMAGE_DEALT_ABSORBED}
     * 按「槽 3 − 槽 2」已经定了，后面只剩「amount 为 0 就跳过」和扣血，改 amount 不会把统计带歪。
     * 全被吸收时照样发（值为 0）；取消 = 置 0，原版 {@code if (amount != 0)} 自然跳过扣血。
     * 发完调一次 {@link AbsorptionShield#afterAbsorb}：吸收被打掉一部分后，把本模组加的吸收上限缩回去
     * （同 NeoForge 的 {@code DamageStages.damage}）。
     * </p>
     */
    @ModifyVariable(method = "actuallyHurt", at = @At("STORE"), ordinal = 2)
    private float carianstyle$damageStage(float absorbed,
                                          @Local(argsOnly = true) DamageSource source,
                                          @Local(argsOnly = true) LocalFloatRef amount) {
        LivingEntity self = (LivingEntity) (Object) this;
        LivingDamageEvent event = CarianBus.post(new LivingDamageEvent(self, source, amount.get()));
        amount.set(event.isCanceled() ? 0.0F : event.getAmount());
        AbsorptionShield.afterAbsorb(self);
        return absorbed;
    }

    /**
     * 跳过 {@code actuallyHurt} 里扣完血之后的第二次 {@code setAbsorptionAmount(吸收 − amount)}（偏移 140），
     * 对齐 NeoForge 1.21.1（它把这一句删了，只扣一次吸收）。
     * <p>
     * 原版不改值时这一句恒为空操作：走到这里吸收已经扣到 0，再减被夹回 0。只有伤害段把值改大、
     * 或者伤害段里给了吸收时才有区别：不跳过的话，加上去的伤害会再从剩余（或新给的）吸收里扣一遍。
     * {@code Player.actuallyHurt} 只有一次 {@code setAbsorptionAmount}，玩家不受影响。
     * 1.20.1 不加这处：Forge 1.20.1 留着第二次扣减。
     * </p>
     */
    @WrapWithCondition(method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V",
                    ordinal = 1))
    private boolean carianstyle$skipSecondAbsorption(LivingEntity self, float value) {
        return false;
    }
}
