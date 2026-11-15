package pers.roinflam.carianstyle.fabric.mixin.enchant;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

/**
 * 铁砧、{@code enchant_randomly} 问 {@code canEnchant} 时，本模组附魔改走 {@code canApplyAtEnchantingTable}，
 * 和 Forge 上 {@code canEnchant} 转调它的结果一致。原版这里也是读 {@code category}，理由同附魔台那个 mixin。
 */
@Mixin(Enchantment.class)
public abstract class FabricEnchantmentMixin {

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void carianstyle$askModEnchantment(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof EnchantmentBase base) {
            cir.setReturnValue(base.canApplyAtEnchantingTable(stack));
        }
    }
}
