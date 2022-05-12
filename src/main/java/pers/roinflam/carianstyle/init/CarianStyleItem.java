package pers.roinflam.carianstyle.init;

import net.minecraft.item.Item;
import net.minecraft.item.ItemSword;
import pers.roinflam.carianstyle.item.entity.ItemGlintblades;

import java.util.ArrayList;
import java.util.List;

public class CarianStyleItem {
    public static final List<Item> ITEMS = new ArrayList<Item>();

    public static final ItemSword GLINTBLADES = new ItemGlintblades("glintblades", null, Item.ToolMaterial.DIAMOND);
}