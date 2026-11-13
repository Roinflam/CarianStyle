package pers.roinflam.carianstyle.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

import java.util.List;
import java.util.Optional;

/**
 * 不写 {@code options} 的随机附魔（{@code enchant_randomly}）不出被禁用的本模组附魔
 *
 * <h3>问题</h3>
 * <p>
 * 被 {@code uninstallEnchantment} 禁用的附魔仍然注册着（存档里带它的物品要读得回来），只是不进任何获取途径标签。
 * 原版战利品表都写了 {@code "options": "#minecraft:on_random_loot"}，禁用的出不来；但不写 {@code options} 的
 * {@code enchant_randomly}（别的模组、数据包常这么写）在 1.21.1 直接取注册表里的全部附魔，禁用的照样会抽到。
 * 1.20.1 这条路只取 {@code isDiscoverable} 的，禁用的出不来。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 在 {@code run} 里候选列表存进局部变量时（{@code STORE}）改写它：没写 {@code options} 时滤掉本模组被禁用的附魔。
 * 写了 {@code options} 的（指定了标签或列表）一律不动，和 1.20.1 一样；写死列表里的禁用附魔由
 * {@code DisabledEnchantmentDropFilter} 在掉落物生成时兜底剥掉。
 * </p>
 * <p>
 * 不用全局战利品修改器：那样会连写死列表的也剥掉，比 1.20.1 严。原版附魔、别的模组的附魔不受影响。
 * </p>
 *
 * @author RoinFlam
 */
@Mixin(EnchantRandomlyFunction.class)
public abstract class MixinEnchantRandomly {

    @Shadow
    @Final
    private Optional<HolderSet<Enchantment>> options;

    @ModifyVariable(
            method = "run(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/storage/loot/LootContext;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("STORE"),
            ordinal = 0
    )
    private List<Holder<Enchantment>> carianstyle$dropDisabled(List<Holder<Enchantment>> candidates) {
        if (this.options.isPresent()) {
            return candidates;
        }
        boolean any = false;
        for (Holder<Enchantment> holder : candidates) {
            if (carianstyle$isDisabled(holder)) {
                any = true;
                break;
            }
        }
        if (!any) {
            return candidates;
        }
        return candidates.stream().filter(holder -> !carianstyle$isDisabled(holder)).toList();
    }

    @Unique
    private static boolean carianstyle$isDisabled(Holder<Enchantment> holder) {
        EnchantmentBase base = EnchantmentRegistry.byHolder(holder);
        return base != null && base.isDisabled();
    }
}
