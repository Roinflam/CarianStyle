package pers.roinflam.carianstyle.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 不写 {@code options} 的按等级附魔（{@code enchant_with_levels}，以及别的模组直接调
 * {@code EnchantmentHelper.enchantItem(..., Optional.empty())}）不出被禁用的本模组附魔
 *
 * <h3>问题</h3>
 * <p>
 * 这条路在 1.21.1 不写 {@code options} 时取注册表里的全部附魔，再按 {@code isPrimaryItemFor} 过滤。
 * 禁用的附魔适用物品是空列表，装备上抽不到；但书不查适用物品，照样会抽出禁用附魔的附魔书。
 * 1.20.1 的 {@code enchantItem} 只取 {@code isDiscoverable} 且附魔台 / 书都允许的，禁用的出不来。
 * 原版战利品表都写了 {@code options}，碰不到；别的模组、数据包不写的会碰到。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 和 {@link MixinEnchantRandomly} 一样只管没写 {@code options} 的：方法开头遇到空的 {@code options}，
 * 就拿「注册表里除了本模组被禁用的以外全部」当 {@code options} 重新调一次同一个方法。写了 {@code options}（标签或列表）的一律不动；
 * 没有禁用任何附魔时直接放过，不建列表。
 * </p>
 *
 * <h3>行为影响</h3>
 * <p>
 * 只少了禁用附魔这几个候选，别的附魔的抽取权重、顺序不变。
 * </p>
 *
 * @author RoinFlam
 */
@Mixin(EnchantmentHelper.class)
public abstract class MixinEnchantItemDefault {

    @Inject(
            method = "enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/RegistryAccess;Ljava/util/Optional;)Lnet/minecraft/world/item/ItemStack;",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void carianstyle$dropDisabled(RandomSource random, ItemStack stack, int level, RegistryAccess access,
                                                 Optional<? extends HolderSet<Enchantment>> options,
                                                 CallbackInfoReturnable<ItemStack> cir) {
        if (options.isPresent() || ConfigLoader.uninstallEnchantment.length == 0) {
            return;
        }
        Registry<Enchantment> registry = access.registryOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> kept = new ArrayList<>(registry.size());
        boolean dropped = false;
        for (Holder<Enchantment> holder : registry.holders().toList()) {
            EnchantmentBase base = EnchantmentRegistry.byHolder(holder);
            if (base != null && base.isDisabled()) {
                dropped = true;
            } else {
                kept.add(holder);
            }
        }
        if (dropped) {
            // 换成写了 options 的同一个方法再调一次，原方法剩下的部分不再跑
            cir.setReturnValue(EnchantmentHelper.enchantItem(random, stack, level, access, Optional.of(HolderSet.direct(kept))));
        }
    }
}
