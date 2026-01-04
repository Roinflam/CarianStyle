package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.flame.FlameBase;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 癫痫火焰燃烧药水效果（隐藏）
 * <p>
 * 效果：
 * - 治疗量减少90%
 * <p>
 * 火焰外观：黄色火焰
 * </p>
 */
public class MobEffectEpilepsyFireBurning extends FlameBase {

    public MobEffectEpilepsyFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "epilepsy_fire_burning");
    }

    /**
     * 治疗量减少90%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase healer = evt.getEntityLiving();
        if (healer.isPotionActive(this)) {
            evt.setAmount(evt.getAmount() * 0.1f);
        }
    }

    @Override
    public int getSerialNumber() {
        return 1;
    }

    @Nonnull
    @Override
    protected String getLevelOneName() {
        return Reference.MOD_ID + ":blocks/yellow_flame_layer_0";
    }

    @Nonnull
    @Override
    protected String getLevelTwoName() {
        return Reference.MOD_ID + ":blocks/yellow_flame_layer_1";
    }
}