package pers.roinflam.kaliastyle.potion.hide;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;
import pers.roinflam.kaliastyle.utils.Reference;
import pers.roinflam.kaliastyle.utils.util.PotionUtil;

@Mod.EventBusSubscriber
public class MobEffectDoomedDeath extends Potion {

    public MobEffectDoomedDeath(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, "doomed_death");
        KaliaStylePotion.POTIONS.add(this);

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "58993fe2-d11c-2b97-4958-6a8304ff8ad8", -0.25, 2);
    }

    public static Potion getPotion() {
        return KaliaStylePotion.DOOMED_DEATH;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return false;
    }

    @Override
    public boolean shouldRender(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderInvText(PotionEffect effect) {
        return false;
    }

    @Override
    public boolean shouldRenderHUD(PotionEffect effect) {
        return false;
    }

}
