package pers.roinflam.carianstyle.block.light;

import net.minecraft.block.BlockAir;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.utils.util.BlockUtil;

public class HideLight extends BlockAir implements ITileEntityProvider {

    public HideLight(String name) {
        super();
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
        setLightLevel(1F);
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MoveLight();
    }
}
