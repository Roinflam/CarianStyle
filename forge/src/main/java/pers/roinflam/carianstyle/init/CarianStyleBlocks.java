package pers.roinflam.carianstyle.init;

import net.minecraft.block.Block;
import pers.roinflam.carianstyle.block.fire.CrimsonFlame;
import pers.roinflam.carianstyle.block.fire.WhiteFlame;
import pers.roinflam.carianstyle.block.fire.YellowFlame;
import pers.roinflam.carianstyle.block.light.HideLight;

import java.util.ArrayList;
import java.util.List;

public class CarianStyleBlocks {

    public static final List<Block> BLOCKS = new ArrayList<Block>();

    public static final CrimsonFlame CRIMSON_FLAME = new CrimsonFlame("crimson_flame", null);
    public static final WhiteFlame WHITE_FLAME = new WhiteFlame("white_flame", null);
    public static final YellowFlame YELLOW_FLAME = new YellowFlame("yellow_flame", null);

    public static final HideLight HIDE_LIGHT = new HideLight("hide_light");

}