package pers.roinflam.carianstyle.mixin.damage;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.event.damage.DamageStages;

/**
 * 伤害适配层的玩家部分：{@link Player} 覆写了 {@code hurt} 和 {@code actuallyHurt}（后者不调 super），
 * 三个阶段要在它自己的方法里发（说明见 {@link MixinLivingEntityDamageStages}）。
 *
 * <h3>注入点（行号是 1.21.1 + NeoForge 21.1.265 的 {@code Player}）</h3>
 * <ul>
 *   <li><b>攻击阶段</b>：{@code Player.hurt} 开头（889 行），在无敌 / 创造 / 已死判断和按难度缩放伤害之前，
 *       和 1.20.1 Forge 在 {@code Player.hurt} 第一行发 {@code onPlayerAttack} 同位置。
 *       {@code ServerPlayer.hurt} 的出生保护、PVP 检查在调 super 之前，1.20.1 也是这样。</li>
 *   <li><b>受伤阶段</b>：{@code Player.actuallyHurt} 开头（989 行）。</li>
 *   <li><b>伤害阶段</b>：{@code Player.actuallyHurt} 里 {@code setAbsorptionAmount} 之后（996 行）。
 *       这里调用的所有者是 {@code Player}（字节码里是 {@code Player.setAbsorptionAmount}），目标描述符照写。</li>
 * </ul>
 * <p>
 * 写成 {@code extends LivingEntity} 的抽象类，直接访问 NeoForge 补进去的受保护字段 {@code damageContainers}。
 * 客户端的 {@code LocalPlayer.hurt} 直接返回 false、不调 super，攻击阶段发不到；本模组的攻击监听全都先判
 * {@code isClientSide}，客户端这一发本来就没人处理，不补。
 * </p>
 */
@Mixin(Player.class)
public abstract class MixinPlayerDamageStages extends LivingEntity {

    protected MixinPlayerDamageStages(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$attackStage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        if (DamageStages.attack(this, source, amount)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "actuallyHurt", at = @At("HEAD"), cancellable = true)
    private void carianstyle$hurtStage(DamageSource source, float amount, CallbackInfo ci) {
        if (DamageStages.hurt(this, source, this.damageContainers)) {
            ci.cancel();
        }
    }

    @Inject(method = "actuallyHurt",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;setAbsorptionAmount(F)V",
                    shift = At.Shift.AFTER))
    private void carianstyle$damageStage(DamageSource source, float amount, CallbackInfo ci) {
        DamageStages.damage(this, source, this.damageContainers);
    }
}
