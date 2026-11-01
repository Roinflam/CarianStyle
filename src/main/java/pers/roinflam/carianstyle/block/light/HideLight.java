package pers.roinflam.carianstyle.block.light;

import net.minecraft.block.BlockAir;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import pers.roinflam.carianstyle.init.CarianStyleBlocks;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.utils.util.BlockUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 隐藏光源方块
 * <p>
 * 用于实现动态光源效果的透明发光方块
 * 特性：
 * - 外观为空气（不可见）
 * - 亮度由方块状态 {@link #LEVEL} 决定（0~15，默认 15），存在元数据里
 * - 不可被其他方块替换
 * - 配合MoveLight实体自动消失
 * </p>
 * <p>
 * 亮度改成逐方块的状态：以前是对方块单例调 {@code setLightLevel}，全服共用一个亮度值，
 * 几个玩家穿不同等级的星光时，最后结算的那个决定所有光源的亮度。
 * </p>
 */
public class HideLight extends BlockAir implements ITileEntityProvider {

    /** 光照等级 */
    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 15);

    public HideLight(String name) {
        super();
        BlockUtil.registerBlock(this, name, null, false);
        CarianStyleBlocks.BLOCKS.add(this);
        setDefaultState(blockState.getBaseState().withProperty(LEVEL, 15));
    }

    @Nonnull
    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, LEVEL);
    }

    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(LEVEL, meta & 15);
    }

    @Override
    public int getMetaFromState(@Nonnull IBlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getLightValue(@Nonnull IBlockState state) {
        return state.getValue(LEVEL);
    }

    @Override
    public int getLightValue(@Nonnull IBlockState state, IBlockAccess world, BlockPos pos) {
        return state.getValue(LEVEL);
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
