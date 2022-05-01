package pers.roinflam.kaliastyle.block.fire;

import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pers.roinflam.kaliastyle.init.KaliaStyleBlocks;
import pers.roinflam.kaliastyle.utils.IHasModel;
import pers.roinflam.kaliastyle.utils.util.BlockUtil;

import java.util.Random;

public class YellowFlame extends BlockFire implements IHasModel {

    public YellowFlame(String name, CreativeTabs creativeTabs) {
        BlockUtil.registerBlock(this, name, null, false);
        KaliaStyleBlocks.BLOCKS.add(this);
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {

    }

    @Override
    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn) {
        worldIn.setBlockToAir(pos);
    }

}

