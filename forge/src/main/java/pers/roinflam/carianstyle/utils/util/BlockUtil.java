package pers.roinflam.carianstyle.utils.util;

import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemBlock;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class BlockUtil {

    @Nullable
    public static ItemBlock registerBlock(@Nonnull Block block, @Nonnull String name, @Nonnull CreativeTabs creativeTabs, boolean itemBlock) {
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
