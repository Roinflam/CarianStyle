package pers.roinflam.carianstyle.potion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.PotionUtil;

@Mod.EventBusSubscriber
public class MobEffectProtectionOfTheErdtree extends Potion {
    private final static ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Reference.MOD_ID, "textures/effect/protection_of_the_erdtree.png");

    public MobEffectProtectionOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, "protection_of_the_erdtree");
        CarianStylePotion.POTIONS.add(this);
    }

    public static Potion getPotion() {
        return CarianStylePotion.PROTECTION_OF_THE_ERDTREE;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            DamageSource damageSource = evt.getSource();
            if (!damageSource.canHarmInCreative() && !damageSource.isDamageAbsolute()) {
                if (!(damageSource.getTrueSource() instanceof EntityLivingBase)) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    if (hurter.isPotionActive(getPotion())) {
                        int amplifier = hurter.getActivePotionEffect(getPotion()).getAmplifier();
                        evt.setAmount((float) (evt.getAmount() - evt.getAmount() * (amplifier + 1) * 0.2));
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
