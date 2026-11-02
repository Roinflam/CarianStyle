package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.mc112.attr.Operation;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 不祥预感药水效果
 * <p>
 * 效果：
 * - 攻击伤害降低20%
 * - 攻击速度降低20%
 * - 受到伤害增加25%
 * - 治疗量减少50%
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class MobEffectBadOmen extends IconBase {

    public MobEffectBadOmen(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        // 1.12.2 版：registerPotionAttributeModifier 拿药水名当修饰符名、名字为空会抛异常，
        // 注册器要到构造之后才设名字，这里先设上同一个键
        this.setPotionName("effect." + Reference.MOD_ID + ".bad_omen");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_DAMAGE,
                "5154bf3b-743a-cee6-cf4f-2a62cf832d25",
                -0.2,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_SPEED,
                "d545b2e6-98a0-c8c7-a7f3-345df7ec14dc",
                -0.2,
                Operation.MULTIPLY_TOTAL.id()
        );
    }

    /**
     * 受到伤害增加25%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        if (victim.isPotionActive(CarianStylePotion.BAD_OMEN.get())) {
            evt.setAmount(evt.getAmount() * 1.25f);
        }
    }

    /**
     * 治疗量减少50%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase healer = evt.getEntityLiving();
        if (healer.isPotionActive(CarianStylePotion.BAD_OMEN.get())) {
            evt.setAmount(evt.getAmount() * 0.5f);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/bad_omen.png");
    }
}
