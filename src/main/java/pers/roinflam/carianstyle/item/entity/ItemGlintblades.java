package pers.roinflam.carianstyle.item.entity;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemSword;
import pers.roinflam.carianstyle.init.CarianStyleItem;
import pers.roinflam.carianstyle.utils.IHasModel;
import pers.roinflam.carianstyle.utils.util.ItemUtil;

import javax.annotation.Nonnull;

public class ItemGlintblades extends ItemSword implements IHasModel {

    public ItemGlintblades(String name, CreativeTabs creativeTabs, @Nonnull ToolMaterial material) {
        super(material);

        ItemUtil.registerItem(this, name, creativeTabs);
        CarianStyleItem.ITEMS.add(this);
    }

}

