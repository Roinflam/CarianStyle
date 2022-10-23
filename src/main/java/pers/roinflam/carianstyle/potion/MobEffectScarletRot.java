package pers.roinflam.carianstyle.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.potion.icon.IconBase;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;


public class MobEffectScarletRot extends IconBase {

    public MobEffectScarletRot(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn, "scarlet_rot");

        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR, "4ffde3df-c955-f645-d34b-814fda024326", -0.5, 2);
        this.registerPotionAttributeModifier(SharedMonsterAttributes.ARMOR_TOUGHNESS, "0b4792a8-c918-bf55-5c7a-62a83b54e569", -0.5, 2);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase healer = evt.getEntityLiving();
            if (healer.isPotionActive(this)) {
                evt.setAmount(evt.getAmount() * 0.75f);
            }
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        if (EntityUtil.getFire(entityLivingBaseIn) <= 0 || RandomUtil.percentageChance(25)) {
            float damage = entityLivingBaseIn.getHealth() * 0.03f + entityLivingBaseIn.getMaxHealth() * 0.00075f;
            damage += damage * amplifier * 0.33;
            entityLivingBaseIn.attackEntityFrom(NewDamageSource.SCARLET_ROT, damage);
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 20 == 0;
    }

    @Nonnull
    @Override
    protected ResourceLocation getResourceLocation() {
        return new ResourceLocation(Reference.MOD_ID, "textures/effect/scarlet_rot.png");
    }
}
