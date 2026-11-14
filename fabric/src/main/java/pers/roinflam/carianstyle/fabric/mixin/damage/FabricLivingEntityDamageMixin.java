package pers.roinflam.carianstyle.fabric.mixin.damage;

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
import pers.roinflam.carianstyle.bus.CarianBus;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;

/**
 * 非玩家生物的伤害三段（攻击、受伤、伤害），位置和 Forge 1.20.1 的三个伤害事件一一对应。玩家的在 {@link FabricPlayerDamageMixin}。
 * 原版没有伤害容器栈，嵌套 {@code hurt} 互不影响。
 */
@Mixin(LivingEntity.class)
public abstract class FabricLivingEntityDamageMixin {

    // 玩家跳过：Player.hurt 开头另发一次，这里再发就两次了
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

    /*
     * 受伤段挂 HEAD 自己判一次 isInvulnerableTo，和 Forge onLivingHurt 的位置等价。不能挂在 getDamageAfterArmorAbsorb
     * 调用上：参数那时已经压栈，改了不生效。取消时 hurt 照样返回 true，只少了扣血，同 Forge。
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

    /*
     * 伤害段：槽 4（被吸收量，偏移 51）存完时吸收已扣、统计已记，只剩扣血，改槽 2 的伤害值。挂在第一次
     * setAbsorptionAmount 之后会把统计带歪。处理器原样返回槽 4。取消 = 0，原版 if (amount != 0) 自然跳过扣血。
     * 扣血后第二次 setAbsorptionAmount 留着不动，和 Forge 1.20.1 一样（1.21.1 那份对齐 NeoForge 跳过了它）。
     */
    @ModifyVariable(method = "actuallyHurt", at = @At("STORE"), ordinal = 2)
    private float carianstyle$damageStage(float absorbed,
                                          @Local(argsOnly = true) DamageSource source,
                                          @Local(argsOnly = true) LocalFloatRef amount) {
        LivingDamageEvent event = CarianBus.post(
                new LivingDamageEvent((LivingEntity) (Object) this, source, amount.get()));
        amount.set(event.isCanceled() ? 0.0F : event.getAmount());
        return absorbed;
    }
}
