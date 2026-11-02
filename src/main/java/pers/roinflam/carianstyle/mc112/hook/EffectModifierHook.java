package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.handler.EffectModifierHandler;
import pers.roinflam.carianstyle.mc112.util.PotionEffectAccess;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;

/**
 * 加药水效果前交给 {@link EffectModifierHandler}，让野兽健壮、野兽活力、镇定这类附魔改时长 / 等级
 * （顶替 1.20.1 挂在 {@code LivingEntity.addEffect} 方法头的 mixin）。
 * <p>
 * 本类调用 1.20.1 同名类的公开静态入口 {@code EffectModifierHandler.handleEffectModification}，
 * 这是平台层引用玩法类的一个例外。
 * </p>
 * <p>
 * 1.12.2 的 {@code addPotionEffect} 顺序是：{@code isPotionApplicable} → 发 {@code PotionAddedEvent(旧效果, 新效果)}
 * → 新效果直接存进效果表，或者 {@code 旧效果.combine(新效果)}。事件里拿到的就是马上要存进去（或合并进去）的那个实例，
 * 原版本来也是把传进来的实例直接存表、每 tick 递减它，所以原地改它的时长和等级和原版语义一致。
 * 和 1.20.1 的差别只是改的时机在 {@code isPotionApplicable} 之后（1.20.1 在 {@code canBeAffected} 之前），结果一样。
 * </p>
 * <p>
 * 事件在 {@code addPotionEffect} 方法体里、真正写表之前发出，Mohist 系混合端的 Bukkit 效果队列在外层，
 * 所以不需要 1.20.1 那个探测混合端效果 tick 的类。
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public final class EffectModifierHook {

    private EffectModifierHook() {
    }

    @SubscribeEvent
    public static void onPotionAdded(@Nonnull PotionEvent.PotionAddedEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity == null || entity.world.isRemote) {
            return;
        }
        PotionEffect effect = event.getPotionEffect();
        PotionEffect result = EffectModifierHandler.handleEffectModification(entity, effect);
        if (result == null || result == effect) {
            return;
        }
        if (result.getPotion() != effect.getPotion()) {
            LogUtil.debug("卡利亚式附魔 - 效果修改换了药水种类（%s → %s），不支持，忽略",
                    effect.getEffectName(), result.getEffectName());
            return;
        }
        if (result.getDuration() != effect.getDuration()) {
            PotionEffectAccess.setDuration(effect, result.getDuration());
        }
        if (result.getAmplifier() != effect.getAmplifier()) {
            PotionEffectAccess.setAmplifier(effect, result.getAmplifier());
        }
    }
}
