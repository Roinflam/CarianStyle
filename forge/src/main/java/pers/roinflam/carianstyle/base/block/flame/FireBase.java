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

/**
 * 自定义火焰方块基类
 * <p>
 * 用于创建不会自然蔓延的装饰性火焰方块
 * 点击即可熄灭
 * </p>
 */
public abstract class FireBase extends BlockFire implements IHasModel {

    public FireBase(String name, CreativeTabs creativeTabs) {
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
        // 不执行火焰蔓延逻辑
    }

    @Override
    public void onBlockClicked(@Nonnull World worldIn, BlockPos pos, EntityPlayer playerIn) {
        // 点击熄灭火焰
        worldIn.setBlockToAir(pos);
    }
}