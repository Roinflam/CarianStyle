package pers.roinflam.carianstyle.base.potion;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.PotionEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.potion.hide.HideBase;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public abstract class NetworkBase extends HideBase {

    protected NetworkBase(boolean isBadEffectIn, int liquidColorIn, String name) {
        super(isBadEffectIn, liquidColorIn, name);
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionRemove(@Nonnull PotionEvent.PotionRemoveEvent evt) {
        if (evt.getPotion().equals(this)) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            @Nullable MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
            if (minecraftServer != null) {
                for (@Nonnull EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                    if (entityLivingBase.world.equals(entityPlayer.world)) {
                        NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(getSerialNumber(), entityPlayer, entityLivingBase.getEntityId(), false);
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPotionExpiry(@Nonnull PotionEvent.PotionExpiryEvent evt) {
        if (evt.getPotionEffect().getPotion().equals(this)) {
            EntityLivingBase entityLivingBase = evt.getEntityLiving();
            @Nullable MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
            if (minecraftServer != null) {
                for (@Nonnull EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                    if (entityLivingBase.world.equals(entityPlayer.world)) {
                        NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(getSerialNumber(), entityPlayer, entityLivingBase.getEntityId(), false);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote && evt.phase.equals(TickEvent.Phase.END)) {
            @Nonnull EntityLivingBase entityLivingBase = evt.player;
            if (!entityLivingBase.isEntityAlive()) {
                @Nullable MinecraftServer minecraftServer = entityLivingBase.world.getMinecraftServer();
                if (minecraftServer != null) {
                    for (@Nonnull EntityPlayer entityPlayer : entityLivingBase.world.getMinecraftServer().getPlayerList().getPlayers()) {
                        if (entityLivingBase.world.equals(entityPlayer.world)) {
                            NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(getSerialNumber(), entityPlayer, entityLivingBase.getEntityId(), false);
                        }
                    }
                }
            }
        }
    }

    @Override
    public void performEffect(@Nonnull EntityLivingBase entityLivingBaseIn, int amplifier) {
        @Nullable MinecraftServer minecraftServer = entityLivingBaseIn.world.getMinecraftServer();
        if (minecraftServer != null) {
            for (@Nonnull EntityPlayer entityPlayer : entityLivingBaseIn.world.getMinecraftServer().getPlayerList().getPlayers()) {
                if (entityLivingBaseIn.world.equals(entityPlayer.world)) {
                    NetworkRegistryHandler.RenderingEffect.sendClientCustomPacket(getSerialNumber(), entityPlayer, entityLivingBaseIn.getEntityId(), true);
                }
            }
        }
    }

    public abstract int getSerialNumber();

    public List<Integer> getActionPotion() {
        return NetworkRegistryHandler.RenderingEffect.getEntitiesID(getSerialNumber());
    }

    public boolean isAction(int id) {
        return getActionPotion().contains(id);
    }

}
