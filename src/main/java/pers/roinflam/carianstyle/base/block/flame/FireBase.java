package pers.roinflam.carianstyle.base.block.flame;

import net.minecraft.block.BlockFire;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.utils.IHasModel;
import pers.roinflam.carianstyle.utils.util.BlockUtil;

import javax.annotation.Nonnull;
import java.util.Random;

public abstract class FireBase extends BlockFire implements IHasModel {

    public FireBase(String name, CreativeTabs creativeTabs) {
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {

    }

    @Override
    public void onBlockClicked(@Nonnull World worldIn, BlockPos pos, EntityPlayer playerIn) {
        worldIn.setBlockToAir(pos);
    }
}
