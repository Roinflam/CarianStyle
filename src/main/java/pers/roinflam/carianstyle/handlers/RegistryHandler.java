package pers.roinflam.carianstyle.handlers;

import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import pers.roinflam.carianstyle.CarianStyle;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.*;
import pers.roinflam.carianstyle.utils.IHasModel;

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

    @SubscribeEvent
    public static void onEntityEntryRegister(RegistryEvent.Register<EntityEntry> evt) {
        evt.getRegistry().registerAll(CarianStyleEntity.ENTITY_ENTRIES.toArray(new EntityEntry[0]));
    }

    @SubscribeEvent
    public static void onItemRegister(RegistryEvent.Register<Item> evt) {
        evt.getRegistry().registerAll(CarianStyleItem.ITEMS.toArray(new Item[0]));
    }

    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent evt) {
        for (Item item : CarianStyleItem.ITEMS) {
            if (item instanceof IHasModel) {
                CarianStyle.proxy.registerItemRenderer(item, 0, "inventory");
            }
        }
    }

}
