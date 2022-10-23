package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AbstractAttributeMap;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import java.util.UUID;


public class MobEffectIncision extends IconBase {
    public static final UUID ID = UUID.fromString("0a6b62ca-ead9-3641-c4dd-a4d33daf5cc1");
    public static final String NAME = "potion.incision";

    public MobEffectIncision(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "incision");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, "0788fd21-aade-d9dc-0daa-faee0f26e5ee", 0.4, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_SPEED, "07a1b38c-e245-d4c0-1e0e-6529582fbb6d", 0.8, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "53c9ebac-b292-2d82-993a-cb183e208411", -0.75, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "68d0f463-1a46-6e25-2ed1-c0aec31b641e", -0.75, 2);
    }

    @Override
    public void removeAttributesModifiersFromEntity(@Nonnull EntityLivingBase entityLivingBaseIn, AbstractAttributeMap attributeMapIn, int amplifier) {
        entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).removeModifier(ID);
        super.removeAttributesModifiersFromEntity(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    @Override
    public void applyAttributesModifiersToEntity(@Nonnull EntityLivingBase entityLivingBaseIn, AbstractAttributeMap attributeMapIn, int amplifier) {
        entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).applyModifier(new AttributeModifier(ID, NAME, 1.2, 2));
        super.applyAttributesModifiersToEntity(entityLivingBaseIn, attributeMapIn, amplifier);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * 1.6f);
            }
        }
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        float damage = entityLivingBaseIn.getMaxHealth() * 0.125f / 20;
        new SynchronizationTask(1) {

            @Override
            public void run() {
                if (entityLivingBaseIn.getHealth() - damage * 2 > 0) {
                    entityLivingBaseIn.setHealth(entityLivingBaseIn.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(entityLivingBaseIn, NewDamageSource.HEMORRHAGE);
                }
            }

        }.start();
        @Nonnull IAttributeInstance attributeInstance = entityLivingBaseIn.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
        if (attributeInstance.getModifier(ID) != null) {
            double base = attributeInstance.getModifier(ID).getAmount();
            if (base > 0.3f) {
                attributeInstance.removeModifier(ID);
                attributeInstance.applyModifier(new AttributeModifier(ID, NAME, base - 0.15 / 20f, 2));
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/incision.png");
    }
}
