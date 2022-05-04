package pers.roinflam.carianstyle.handlers;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;

@Mod.EventBusSubscriber
public class RegistryHandler {

    @SubscribeEvent
    public static void onEnchantmentRegister(RegistryEvent.Register<Enchantment> evt) {
        CarianStyleEnchantments.ENCHANTMENTS.removeIf(enchantment -> ConfigLoader.uninstall_enchantment.contains(enchantment.getName()));
        evt.getRegistry().registerAll(CarianStyleEnchantments.ENCHANTMENTS.toArray(new Enchantment[0]));
    }

    @SubscribeEvent
    public static void onPotionRegister(RegistryEvent.Register<Potion> evt) {
        evt.getRegistry().registerAll(CarianStylePotion.POTIONS.toArray(new Potion[0]));
    }

    @SubscribeEvent
    public static void onBlockRegister(RegistryEvent.Register<Block> evt) {
        evt.getRegistry().registerAll(CarianStyleBlocks.BLOCKS.toArray(new Block[0]));
    }

}
