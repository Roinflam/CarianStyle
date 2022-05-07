package pers.roinflam.carianstyle.potion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.PotionUtil;

@Mod.EventBusSubscriber
public class MobEffectGoldenVow extends Potion {
    private final static ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Reference.MOD_ID, "textures/effect/golden_vow.png");

    public MobEffectGoldenVow(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, "golden_vow");
        CarianStylePotion.POTIONS.add(this);
    }

    public static Potion getPotion() {
        return CarianStylePotion.GOLDEN_VOW;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.isPotionActive(getPotion())) {
                    int amplifier = hurter.getActivePotionEffect(getPotion()).getAmplifier();
                    evt.setAmount((float) (evt.getAmount() - evt.getAmount() * (amplifier + 1) * 0.1));
                }
                if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    if (attacker.isPotionActive(getPotion())) {
                        int amplifier = attacker.getActivePotionEffect(getPotion()).getAmplifier();
                        evt.setAmount((float) (evt.getAmount() + evt.getAmount() * (amplifier + 1) * 0.15));
                    }
                }
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderInventoryEffect(int x, int y, PotionEffect effect, Minecraft mc) {
        mc.getTextureManager().bindTexture(RESOURCE_LOCATION);
        Gui.drawModalRectWithCustomSizedTexture(x + 6, y + 7, 0, 0, 18, 18, 18, 18);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void renderHUDEffect(int x, int y, PotionEffect effect, Minecraft mc, float alpha) {
        mc.getTextureManager().bindTexture(RESOURCE_LOCATION);
        Gui.drawModalRectWithCustomSizedTexture(x + 3, y + 3, 0, 0, 18, 18, 18, 18);
    }
}
