package pers.roinflam.carianstyle.block.fire;

import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.utils.IHasModel;
import pers.roinflam.carianstyle.utils.util.BlockUtil;

import java.util.Random;

public class YellowFlame extends BlockFire implements IHasModel {

    public YellowFlame(String name, CreativeTabs creativeTabs) {
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {

    }

    @Override
    public void onBlockClicked(World worldIn, BlockPos pos, EntityPlayer playerIn) {
        worldIn.setBlockToAir(pos);
    }

}

