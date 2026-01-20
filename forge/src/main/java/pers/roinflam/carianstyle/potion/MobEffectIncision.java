package pers.roinflam.carianstyle.potion;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 切割药水效果
 * <p>
 * 效果：
 * - 攻击伤害+40%
 * - 攻击速度+80%
 * - 护甲-75%
 * - 韧性-75%
 * - 移动速度初始+120%，随时间衰减至+30%
 * - 治疗量+60%
 * - 每tick扣除最大生命值×12.5%/20的血量（可致死）
 * </p>
 */
@Mod.EventBusSubscriber
public class MobEffectIncision extends IconBase {

    public static final UUID ID = UUID.fromString("0a6b62ca-ead9-3641-c4dd-a4d33daf5cc1");
    public static final String NAME = "potion.incision";

    public MobEffectIncision(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn ? MobEffectCategory.HARMFUL : MobEffectCategory.BENEFICIAL, liquidColorIn);

        this.addAttributeModifier(
                Attributes.ATTACK_DAMAGE,
                "0788fd21-aade-d9dc-0daa-faee0f26e5ee",
                0.4,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ATTACK_SPEED,
                "07a1b38c-e245-d4c0-1e0e-6529582fbb6d",
                0.8,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR,
                "53c9ebac-b292-2d82-993a-cb183e208411",
                -0.75,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                "68d0f463-1a46-6e25-2ed1-c0aec31b641e",
                -0.75,
                AttributeModifier.Operation.MULTIPLY_TOTAL
        );
    }

    @Override
    public void removeAttributeModifiers(@Nonnull LivingEntity entityLivingBaseIn,
                                         @Nonnull AttributeMap attributeMapIn,
                                         int amplifier) {
        entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(ID);
        super.removeAttributeModifiers(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    @Override
    public void addAttributeModifiers(@Nonnull LivingEntity entityLivingBaseIn,
                                      @Nonnull AttributeMap attributeMapIn,
                                      int amplifier) {
        // 初始移动速度+120%
        entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED)
                .addTransientModifier(new AttributeModifier(ID, NAME, 1.2, AttributeModifier.Operation.MULTIPLY_TOTAL));
        super.addAttributeModifiers(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    /**
     * 治疗量+60%
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity healer = evt.getEntity();
        if (healer.hasEffect(CarianStylePotion.INCISION.get())) {
            evt.setAmount(evt.getAmount() * 1.6f);
        }
    }

    @Override
    public void applyEffectTick(@Nonnull LivingEntity entityLivingBaseIn, int amplifier) {
        // 每tick扣血（最大生命值×12.5%/20）
        float damage = entityLivingBaseIn.getMaxHealth() * 0.125f / 20;

        new SynchronizationTask(1) {
            @Override
            public void run() {
                if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
                    entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(entityLivingBaseIn, NewDamageSource.hemorrhage(entityLivingBaseIn.level()));
                }
            }
        }.start();

        // 移动速度随时间衰减
        AttributeInstance attributeInstance = entityLivingBaseIn.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeModifier modifier = attributeInstance.getModifier(ID);
        if (modifier != null) {
            double currentBonus = modifier.getAmount();
            // 衰减至30%时停止
            if (currentBonus > 0.3) {
                attributeInstance.removeModifier(ID);
                // 每tick衰减0.75%
                attributeInstance.addTransientModifier(new AttributeModifier(ID, NAME, currentBonus - 0.15 / 20f, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    @OnlyIn(Dist.CLIENT)
    protected ResourceLocation getIconTexture() {
        return new ResourceLocation(Reference.MOD_ID, "textures/mob_effect/incision.png");
    }
}