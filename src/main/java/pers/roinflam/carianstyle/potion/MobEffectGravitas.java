package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.mc112.attr.Operation;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 重力药水效果
 * <p>
 * 效果：
 * - 攻击速度降低1%×等级
 * - 移动速度降低1%×等级
 * - 飞行速度降低1%×等级
 * - 无法跳跃
 * </p>
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class MobEffectGravitas extends IconBase {

    public MobEffectGravitas(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        // 1.12.2 版：registerPotionAttributeModifier 拿药水名当修饰符名、名字为空会抛异常，
        // 注册器要到构造之后才设名字，这里先设上同一个键
        this.setPotionName("effect." + Reference.MOD_ID + ".gravitas");

        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.ATTACK_SPEED,
                "64dd94d7-8d83-122b-82be-0c52223463ca",
                -0.01,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.MOVEMENT_SPEED,
                "53878b9c-1134-c379-59c4-391599537f5e",
                -0.01,
                Operation.MULTIPLY_TOTAL.id()
        );
        this.registerPotionAttributeModifier(
                SharedMonsterAttributes.FLYING_SPEED,
                "710bd865-953f-ed1a-facf-eba7de0ce330",
                -0.01,
                Operation.MULTIPLY_TOTAL.id()
        );
    }

    /**
     * 重力状态下无法跳跃
     * <p>双端都跑：玩家的跳跃在客户端发起，客户端也得顶住跳跃冷却。</p>
     */
    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entityLiving = evt.getEntityLiving();
        if (entityLiving.isPotionActive(CarianStylePotion.GRAVITAS.get())) {
            EntityLivingUtil.setJumped(entityLiving);
        }
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/gravitas.png");
    }
}
