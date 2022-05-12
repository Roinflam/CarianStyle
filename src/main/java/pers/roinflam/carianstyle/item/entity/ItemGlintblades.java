package pers.roinflam.carianstyle.item.entity;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemSword;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.utils.IHasModel;

public class ItemGlintblades extends ItemSword implements IHasModel {

    public ItemGlintblades(String name, CreativeTabs creativeTabs, ToolMaterial material) {
        super(material);

        setUnlocalizedName(name);
        setRegistryName(name);
        setCreativeTab(creativeTabs);

        CarianStyleItem.ITEMS.add(this);
    }

}

