package pers.roinflam.carianstyle.base.block.flame;

import net.minecraft.block.BlockFire;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import java.util.Random;

/**
 * 自定义火焰方块基类
 * <p>
 * 只用来装饰（猩红、白色、黄色三种火焰），不蔓延、不熄灭、不点燃周围的方块，点一下就灭。
 * 亮度 15，踩上去和原版火一样会着火。
 * </p>
 * <p>
 * v2.2修复：移除所有 System.out.println 调试输出
 * System.out.println 是同步IO操作，在服务端每次调用都会阻塞当前线程，
 * 加上字符串拼接的GC开销，对tick性能有负面影响
 * </p>
 * <p>
 * 1.12.2 版：继承 {@link BlockFire}，沿用它的方块状态（四个侧面 + 顶面的连接属性），资源文件里的
 * blockstate 和 1.20.1 的一致。几处和原版火不同的地方都照 1.20.1：
 * </p>
 * <ul>
 *   <li>连接属性按「相邻方块是不是实心面」算，不按「相邻方块能不能烧」算（原版火只贴着可燃方块爬）；</li>
 *   <li>只要下面那一格的顶面是实心的就能存在，下面没了就消失；</li>
 *   <li>不跑原版火的随机刻（不蔓延、不熄灭、不被雨浇灭），放下时也不检查下界传送门；</li>
 *   <li>渲染层是 cutout（1.20.1 在客户端初始化里设，1.12.2 写在 {@link #getBlockLayer()}）。</li>
 * </ul>
 */
public abstract class FireBase extends BlockFire {

    public FireBase() {
        super();
        setLightLevel(1.0F);
        setSoundType(SoundType.CLOTH);
        setHardness(0.0F);
        disableStats();
    }

    /**
     * 连接属性：相邻方块朝这一面是实心的就贴上去（1.20.1 是 {@code !isAir() && isSolid()}）
     */
    @Nonnull
    @Override
    @SuppressWarnings("deprecation")
    public IBlockState getActualState(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        return state
                .withProperty(NORTH, solid(world, pos, EnumFacing.NORTH))
                .withProperty(EAST, solid(world, pos, EnumFacing.EAST))
                .withProperty(SOUTH, solid(world, pos, EnumFacing.SOUTH))
                .withProperty(WEST, solid(world, pos, EnumFacing.WEST))
                .withProperty(UPPER, solid(world, pos, EnumFacing.UP));
    }

    private static boolean solid(@Nonnull IBlockAccess world, @Nonnull BlockPos pos, @Nonnull EnumFacing side) {
        BlockPos adjacent = pos.offset(side);
        IBlockState neighbor = world.getBlockState(adjacent);
        return !neighbor.getBlock().isAir(neighbor, world, adjacent)
                && neighbor.isSideSolid(world, adjacent, side.getOpposite());
    }

    /**
     * 能不能存在：下面那一格的顶面要是实心的（1.20.1 {@code canSurvive}）
     */
    @Override
    public boolean canPlaceBlockAt(@Nonnull World worldIn, @Nonnull BlockPos pos) {
        BlockPos below = pos.down();
        return worldIn.getBlockState(below).isSideSolid(worldIn, below, EnumFacing.UP);
    }

    @Override
    public void neighborChanged(@Nonnull IBlockState state, @Nonnull World worldIn, @Nonnull BlockPos pos,
                                @Nonnull net.minecraft.block.Block blockIn, @Nonnull BlockPos fromPos) {
        if (!canPlaceBlockAt(worldIn, pos)) {
            worldIn.setBlockToAir(pos);
        }
    }

    /**
     * 放下时只检查能不能存在；不排随机刻，也不像原版火那样去点下界传送门
     */
    @Override
    public void onBlockAdded(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull IBlockState state) {
        if (!worldIn.isRemote && !canPlaceBlockAt(worldIn, pos)) {
            worldIn.setBlockToAir(pos);
        }
    }

    @Override
    public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
        // 不执行火焰蔓延逻辑
    }

    @Override
    public boolean requiresUpdates() {
        return false;
    }

    @Override
    public void onBlockClicked(@Nonnull World worldIn, @Nonnull BlockPos pos, @Nonnull EntityPlayer playerIn) {
        // 点击熄灭火焰
        if (!worldIn.isRemote) {
            worldIn.setBlockToAir(pos);
        }
    }

    /**
     * 渲染层：cutout（1.20.1 在客户端初始化里用 {@code ItemBlockRenderTypes.setRenderLayer} 设）
     */
    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT;
    }
}
