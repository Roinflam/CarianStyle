package pers.roinflam.carianstyle;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.network.NetworkRegistryHandler;
import pers.roinflam.carianstyle.proxy.CommonProxy;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Mod(modid = Reference.MOD_ID, useMetadata = true, guiFactory = "pers.roinflam.carianstyle.gui.ConfigGuiFactory")
public class CarianStyle {
    @Mod.Instance
    public static CarianStyle instance;

    @SidedProxy(clientSide = Reference.CLIENT_PROXY_CLASS, serverSide = Reference.COMMON_PROXY_CLASS)
    public static CommonProxy proxy;

    @Mod.EventHandler
    public static void preInit(@Nonnull FMLPreInitializationEvent evt) {
        NetworkRegistryHandler.register();
        proxy.registerEntityRenderer();

        @Nonnull List<EnumEnchantmentType> typeList = new ArrayList<>(Arrays.asList(CreativeTabs.COMBAT.getRelevantEnchantmentTypes()));
        typeList.add(CarianStyleEnchantments.ARMS);
        typeList.add(CarianStyleEnchantments.SHIELD);
        CreativeTabs.COMBAT.setRelevantEnchantmentTypes(typeList.toArray(new EnumEnchantmentType[0]));
        typeList = new ArrayList<>(Arrays.asList(CreativeTabs.TOOLS.getRelevantEnchantmentTypes()));
        typeList.add(CarianStyleEnchantments.PICKAEX);
        CreativeTabs.TOOLS.setRelevantEnchantmentTypes(typeList.toArray(new EnumEnchantmentType[0]));
    }

    @Mod.EventHandler
    public static void init(FMLInitializationEvent evt) {

    }

    @Mod.EventHandler
    public static void postInit(FMLPostInitializationEvent evt) {

    }
}
