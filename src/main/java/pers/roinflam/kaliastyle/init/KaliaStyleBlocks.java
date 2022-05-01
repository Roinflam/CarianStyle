package pers.roinflam.kaliastyle.init;

import net.minecraft.block.Block;
import pers.roinflam.kaliastyle.block.fire.CrimsonFlame;
import pers.roinflam.kaliastyle.block.fire.WhiteFlame;
import pers.roinflam.kaliastyle.block.fire.YellowFlame;

import java.util.ArrayList;
import java.util.List;

public class KaliaStyleBlocks {

    public static final List<Block> BLOCKS = new ArrayList<Block>();

    public static final CrimsonFlame CRIMSON_FLAME = new CrimsonFlame("crimson_flame", null);
    public static final WhiteFlame WHITE_FLAME = new WhiteFlame("white_flame", null);
    public static final YellowFlame YELLOW_FLAME = new YellowFlame("yellow_flame", null);

}