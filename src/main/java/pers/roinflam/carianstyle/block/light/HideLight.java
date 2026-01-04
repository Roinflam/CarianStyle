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

import javax.annotation.Nullable;

/**
 * 隐藏光源方块
 * <p>
 * 用于实现动态光源效果的透明发光方块
 * 特性：
 * - 外观为空气（不可见）
 * - 发出最大亮度的光
 * - 不可被其他方块替换
 * - 配合MoveLight实体自动消失
 * </p>
 */
public class HideLight extends BlockAir implements ITileEntityProvider {

    public HideLight(String name) {
        super();
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
        setLightLevel(1.0F);
    }

    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MoveLight();
    }
}