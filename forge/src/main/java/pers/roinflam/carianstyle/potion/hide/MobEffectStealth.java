package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 隐身药水效果（隐藏）
 * <p>
 * 效果：
 * - 玩家模型不渲染（隐形）
 * - 生物无法将此实体设为攻击目标
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class MobEffectStealth extends HideBase {

    public MobEffectStealth(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);
    }

    /**
     * 客户端：隐藏玩家渲染
     */
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderPlayer(@NotNull RenderPlayerEvent.Pre evt) {
        Player player = evt.getEntity();

        // 检查玩家是否拥有隐身效果
        if (player.hasEffect(CarianStylePotion.STEALTH.get())) {
            evt.setCanceled(true);
        }
    }

    /**
     * 服务端：阻止生物将隐身实体设为目标
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(@NotNull LivingChangeTargetEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getNewTarget() == null) {
            return;
        }

        if (!(evt.getEntity() instanceof Mob)) {
            return;
        }

        LivingEntity target = evt.getNewTarget();

        // 检查目标是否拥有隐身效果
        if (target.hasEffect(pers.roinflam.carianstyle.init.CarianStylePotion.STEALTH.get())) {
            evt.setCanceled(true);
        }
    }
}