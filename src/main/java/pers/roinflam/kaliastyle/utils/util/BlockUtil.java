package pers.roinflam.kaliastyle.utils.util;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;

public class BlockUtil {

    public static ItemBlock registerBlock(Block block, String name, CreativeTabs creativeTabs, boolean itemBlock) {
        block.setUnlocalizedName(name);
        block.setRegistryName(name);
        block.setCreativeTab(creativeTabs);
        if (itemBlock) {
            return (ItemBlock) ItemUtil.registerItem(new ItemBlock(block), block.getRegistryName().toString(), creativeTabs);
        } else {
            return null;
        }
    }

}
