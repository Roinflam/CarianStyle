package pers.roinflam.kaliastyle.handlers;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.enchantment.config.ConfigLoader;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;

@Mod.EventBusSubscriber
public class RegistryHandler {

    @SubscribeEvent
    public static void onPotionRegister(RegistryEvent.Register<Potion> evt) {
        evt.getRegistry().registerAll(KaliaStylePotion.POTIONS.toArray(new Potion[0]));
    }

    @SubscribeEvent
    public static void onEnchantmentRegister(RegistryEvent.Register<Enchantment> evt) {
        KaliaStyleEnchantments.ENCHANTMENTS.removeIf(enchantment -> ConfigLoader.uninstall_enchantment.contains(enchantment.getName()));
        evt.getRegistry().registerAll(KaliaStyleEnchantments.ENCHANTMENTS.toArray(new Enchantment[0]));
    }

}
