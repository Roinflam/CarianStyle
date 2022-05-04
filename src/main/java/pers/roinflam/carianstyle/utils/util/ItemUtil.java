package pers.roinflam.carianstyle.utils.util;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import pers.roinflam.carianstyle.CarianStyle;

public class ItemUtil {

    public static Item registerItem(Item item, String name, CreativeTabs creativeTabs) {
        item.setUnlocalizedName(name);
        item.setRegistryName(name);
        item.setCreativeTab(creativeTabs);
        CarianStyle.proxy.registerItemRenderer(item, 0, "inventory");
        return item;
    }

}
