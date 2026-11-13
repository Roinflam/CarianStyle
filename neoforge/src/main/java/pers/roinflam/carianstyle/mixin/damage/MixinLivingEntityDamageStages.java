package pers.roinflam.carianstyle.mixin.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.event.damage.DamageStages;

import java.util.Stack;

/**
 * 伤害适配层：在 {@link LivingEntity} 的伤害流程里原位发出攻击 / 受伤 / 伤害三个阶段（玩家另见 {@link MixinPlayerDamageStages}）。
 *
 * <h3>三个注入点（行号是 1.21.1 + NeoForge 21.1.265 的 {@code LivingEntity}）</h3>
 * <ul>
 *   <li><b>攻击阶段</b>：{@code hurt} 开头（1114 行，{@code isInvulnerableTo} 之前）。1.20.1 Forge 的
 *       {@code onLivingAttack} 就在这里；玩家的攻击阶段在 {@code Player.hurt} 开头发，这里跳过玩家
 *       （Forge 1.20.1 的 {@code onLivingAttack} 对玩家同样跳过，玩家走 {@code onPlayerAttack}）。
 *       取消时返回 false，不压伤害容器、不写无敌时间。</li>
 *   <li><b>受伤阶段</b>：{@code actuallyHurt} 开头（1739 行）。无敌帧已经写过、盾已经挡过，护甲还没算，
 *       和 1.20.1 {@code actuallyHurt} 开头的 {@code onLivingHurt} 同位置。取消或结果 ≤ 0 时整段跳过（1.20.1 是 {@code return}）。</li>
 *   <li><b>伤害阶段</b>：{@code actuallyHurt} 里 {@code setAbsorptionAmount} 调用之后（1746 行）。
 *       不挂在 NeoForge 的 {@code ABSORPTION} 减免函数里：那时吸收值还没扣，按剩余吸收算的附魔（圣律）会看到扣之前的状态，
 *       监听者改的吸收值还会被下一行再扣一次。挂在之后，和 1.20.1「扣完吸收再发事件、事件只改扣血量」一致；
 *       紧接着的 {@code getNewDamage()} 就是扣血量。</li>
 * </ul>
 * <p>
 * 受伤阶段取消用 {@code ci.cancel()}：别的模组注在 {@code actuallyHurt} 返回处的注入会被跳过，
 * 1.20.1 里 Forge 的 {@code return} 会走到它们。改成「设 0 继续跑」的话别的模组会收到 0 伤害的 NeoForge 事件，
 * 1.20.1 取消后它们什么都收不到，取消更接近。
 * </p>
 * <p>
 * 名字一律 Mojmap（1.21.1 运行期就是 Mojmap，没有 refmap）。{@code damageContainers} 是 NeoForge 补进
 * {@code LivingEntity} 的受保护字段（264 行）。
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityDamageStages {

    @Shadow
    protected Stack<DamageContainer> damageContainers;

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$attackStage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof Player) {
            return;
        }
        if (DamageStages.attack((LivingEntity) (Object) this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$hurtStage(DamageSource source, float amount, CallbackInfo ci) {
        if (DamageStages.hurt((LivingEntity) (Object) this, source, this.damageContainers)) {
            ci.cancel();
        }
    }

    @Inject(method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;setAbsorptionAmount(F)V",
                    shift = At.Shift.AFTER))
    private void carianstyle$damageStage(DamageSource source, float amount, CallbackInfo ci) {
        DamageStages.damage((LivingEntity) (Object) this, source, this.damageContainers);
    }
}
