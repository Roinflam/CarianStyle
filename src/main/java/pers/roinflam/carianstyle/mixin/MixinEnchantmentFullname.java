package pers.roinflam.carianstyle.mixin;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;

/**
 * 附魔名字后面的「（生效 N 级）」
 *
 * <h3>问题</h3>
 * <p>
 * 开了高等级衰减时，1.20.1 覆写附魔的实例方法 {@code getFullname(int)}，在名字后面补上实际生效的等级。
 * 1.21.1 的附魔是 record，名字改由静态方法 {@code Enchantment.getFullname(Holder, int)} 拼
 * （提示框经 {@code ItemEnchantments.addToTooltip} 调它），本模组的附魔类已经不是 {@code Enchantment}，覆写不到了。
 * </p>
 *
 * <h3>做法</h3>
 * <p>
 * 在 {@code getFullname} 返回时注入：按 Holder 的键找本模组的行为对象，交给 {@code EnchantmentBase#appendEffectiveLevel}
 * 决定要不要补后缀（规则和 1.20.1 的覆写逐字一样：衰减开着、{@code showInName} 没关、这一级会被打折才补）。
 * 目标是静态方法，处理方法也必须是 {@code static}，否则加载时才报错。原版附魔、别的模组的附魔不动。
 * </p>
 *
 * @author RoinFlam
 */
@Mixin(Enchantment.class)
public abstract class MixinEnchantmentFullname {

    @Inject(method = "getFullname", at = @At("RETURN"), cancellable = true)
    private static void carianstyle$appendEffectiveLevel(Holder<Enchantment> enchantment, int level,
                                                         CallbackInfoReturnable<Component> cir) {
        EnchantmentBase base = EnchantmentRegistry.byHolder(enchantment);
        if (base == null) {
            return;
        }
        Component name = cir.getReturnValue();
        Component withSuffix = base.appendEffectiveLevel(name, level);
        if (withSuffix != name) {
            cir.setReturnValue(withSuffix);
        }
    }
}
