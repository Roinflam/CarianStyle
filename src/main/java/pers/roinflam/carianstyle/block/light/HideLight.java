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
import pers.roinflam.carianstyle.tileentity.MoveLight;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 隐藏光源方块
 * <p>
 * 用于实现动态光源效果的透明发光方块
 * 特性：
 * - 外观为空气（不可见，渲染类型 INVISIBLE，从来不画）
 * - 亮度由 {@link #LEVEL} 决定（0~15，默认 15），存在元数据里
 * - 不可被其他方块替换
 * - 配合MoveLight实体自动消失
 * </p>
 * <p>
 * 亮度是逐方块的状态：几个玩家穿不同等级的星光时各自的光源亮度互不影响。
 * </p>
 * <p>
 * 1.12.2 版：16 个状态在客户端都映射到同一个 {@code normal} 变体（{@code ClientSetupHandler} 里设状态映射，
 * 资源里有对应的 blockstate 和只带粒子贴图的空模型），不会再报缺模型。没有物品形态。
 * </p>
 */
public class HideLight extends BlockAir implements ITileEntityProvider {

    /** 光照等级，{@link #getLightValue} 读它 */
    public static final PropertyInteger LEVEL = PropertyInteger.create("level", 0, 15);

    public HideLight() {
        super();
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

    /**
     * 判断方块是否可被替换
     *
     * @return 始终返回 false，防止被其他方块替换
     */
    @Override
    public boolean isReplaceable(IBlockAccess world, BlockPos pos) {
        return false;
    }

    /**
     * 创建方块实体
     *
     * @return MoveLight 方块实体实例
     */
    @Nullable
    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new MoveLight();
    }
}
