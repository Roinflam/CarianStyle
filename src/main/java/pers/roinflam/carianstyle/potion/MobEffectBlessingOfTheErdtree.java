package pers.roinflam.carianstyle.potion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.PotionUtil;

@Mod.EventBusSubscriber
public class MobEffectBlessingOfTheErdtree extends Potion {
    private final static ResourceLocation RESOURCE_LOCATION = new ResourceLocation(Reference.MOD_ID, "textures/effect/blessing_of_the_erdtree.png");

    public MobEffectBlessingOfTheErdtree(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, "blessing_of_the_erdtree");
        CarianStylePotion.POTIONS.add(this);

        this.registerPotionAttributeModifier(SharedMonsterAttributes.MAX_HEALTH, "c407bffa-97df-adf8-51db-5681fdef4b8c", 0.15, 2);
    }

    public static Potion getPotion() {
        return CarianStylePotion.BLESSING_OF_THE_ERDTREE;
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return duration % 60 == 0;
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        entityLivingBaseIn.heal((float) (entityLivingBaseIn.getMaxHealth() * (amplifier + 1) * 0.04));
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
