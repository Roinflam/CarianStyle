package pers.roinflam.carianstyle;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.proxy.CommonProxy;
import pers.roinflam.carianstyle.utils.Reference;

@Mod(modid = Reference.MOD_ID, useMetadata = true)
public class CarianStyle {
    @Mod.Instance
    public static CarianStyle instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(FMLPreInitializationEvent evt) {
        new ConfigLoader(evt);
        NetworkRegistryHandler.register();
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {

    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {

    }
}