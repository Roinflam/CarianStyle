package pers.roinflam.carianstyle.potion.hide;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import pers.roinflam.carianstyle.utils.util.PotionUtil;

@Mod.EventBusSubscriber
public class MobEffectDestructionFireBurning extends Potion {
    private final static String ICON_NAME_0 = Reference.MOD_ID + ":blocks/white_flame_layer_0";
    private final static String ICON_NAME_1 = Reference.MOD_ID + ":blocks/white_flame_layer_1";

    public MobEffectDestructionFireBurning(boolean isBadEffectIn, int liquidColorIn) {
        super(isBadEffectIn, liquidColorIn);
        PotionUtil.registerPotion(this, "destruction_fire_burning");
        CarianStylePotion.POTIONS.add(this);
    }

    public static Potion getPotion() {
        return CarianStylePotion.DESTRUCTION_FIRE_BURNING;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotionRemove(PotionEvent.PotionRemoveEvent evt) {
        if (evt.getPotion().equals(getPotion())) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
            if (minecraftServer != null) {
                for (EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                    NetworkRegistryHandler.DestructionFireBurning.sendClientCustomPacket(entityPlayer, entityLivingBase.getEntityId(), false);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPotionExpiry(PotionEvent.PotionExpiryEvent evt) {
        if (evt.getPotionEffect().getPotion().equals(getPotion())) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
            if (minecraftServer != null) {
                for (EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                    NetworkRegistryHandler.DestructionFireBurning.sendClientCustomPacket(entityPlayer, entityLivingBase.getEntityId(), false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && evt.phase.equals(TickEvent.Phase.END)) {
            EntityLivingBase entityLivingBase = evt.player;
            if (!entityLivingBase.isEntityAlive()) {
                MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
                if (minecraftServer != null) {
                    for (EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                        NetworkRegistryHandler.DestructionFireBurning.sendClientCustomPacket(entityPlayer, entityLivingBase.getEntityId(), false);
                    }
                }
            }
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Specials.Post evt) {
        EntityLivingBase entityLivingBase = evt.getEntity();
        if (NetworkRegistryHandler.DestructionFireBurning.getEntitiesID().contains(entityLivingBase.getEntityId())) {
            float partialTicks = evt.getPartialRenderTick();
            double posX = evt.getX() + (entityLivingBase.posX - entityLivingBase.lastTickPosX) * (double) partialTicks;
            double posY = evt.getY() + (entityLivingBase.posY - entityLivingBase.lastTickPosY + 1) * (double) partialTicks;
            double posZ = evt.getZ() + (entityLivingBase.posZ - entityLivingBase.lastTickPosZ) * (double) partialTicks;
            EntityUtil.renderEntityOnFire(entityLivingBase, posX, posY, posZ, ICON_NAME_0, ICON_NAME_1);
        }
    }

    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public static void onRenderSpecificHand(RenderSpecificHandEvent evt) {
        EntityPlayer player = Minecraft.getMinecraft().player;
        if (NetworkRegistryHandler.DestructionFireBurning.getEntitiesID().contains(player.getEntityId())) {
            EntityUtil.renderFireInFirstPerson(ICON_NAME_1);
        }
    }

    @Override
    public void performEffect(EntityLivingBase entityLivingBaseIn, int amplifier) {
        MinecraftServer minecraftServer = entityLivingBaseIn.world.getMinecraftServer();
        if (minecraftServer != null) {
            for (EntityPlayer entityPlayer : entityLivingBaseIn.world.getMinecraftServer().getPlayerList().getPlayers()) {
                NetworkRegistryHandler.DestructionFireBurning.sendClientCustomPacket(entityPlayer, entityLivingBaseIn.getEntityId(), true);
            }
        }
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        return true;
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
