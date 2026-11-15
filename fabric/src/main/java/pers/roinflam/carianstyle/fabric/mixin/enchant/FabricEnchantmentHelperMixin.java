package pers.roinflam.carianstyle.fabric.mixin.enchant;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

/**
 * 附魔台、{@code enchant_with_levels}、刷怪装备抽附魔时，本模组附魔改问它自己的 {@code canApplyAtEnchantingTable}。
 * <p>
 * 原版在这里直接读 {@code category} 判物品，而 Fabric 上本模组附魔的 category 一律是 {@code BREAKABLE}
 * （原版枚举扩展不了），不改的话盾牌附魔会刷到所有可损耗物品上。书的放行在这个判断之后另算，不受影响。
 * </p>
 */
@Mixin(EnchantmentHelper.class)
public abstract class FabricEnchantmentHelperMixin {

    @WrapOperation(method = "getAvailableEnchantmentResults",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/item/enchantment/EnchantmentCategory;canEnchant(Lnet/minecraft/world/item/Item;)Z"))
    private static boolean carianstyle$askModEnchantment(EnchantmentCategory category, Item item, Operation<Boolean> original,
                                                         @Local Enchantment enchantment, @Local(argsOnly = true) ItemStack stack) {
        if (enchantment instanceof EnchantmentBase base) {
            return base.canApplyAtEnchantingTable(stack);
        }
        return original.call(category, item);
    }
}
