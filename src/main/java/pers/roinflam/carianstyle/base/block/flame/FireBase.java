package pers.roinflam.carianstyle.base.block.flame;

import com.google.common.collect.ImmutableMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 自定义火焰方块基类
 * <p>
 * v2.2修复：移除所有 System.out.println 调试输出
 * System.out.println 是同步IO操作，在服务端每次调用都会阻塞当前线程，
 * 加上字符串拼接的GC开销，对tick性能有负面影响
 * </p>
 */
public abstract class FireBase extends BaseFireBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;

    private static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION =
            ImmutableMap.copyOf(
                    Util.make(new java.util.EnumMap<>(Direction.class), map -> {
                        map.put(Direction.NORTH, NORTH);
                        map.put(Direction.EAST, EAST);
                        map.put(Direction.SOUTH, SOUTH);
                        map.put(Direction.WEST, WEST);
                        map.put(Direction.UP, UP);
                    })
            );

    private static final VoxelShape DOWN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    public FireBase(BlockBehaviour.Properties properties) {
        super(properties, 1.0F);
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(NORTH, false)
                        .setValue(EAST, false)
                        .setValue(SOUTH, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
        );
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP);
    }

    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return DOWN_AABB;
    }

    /**
     * 指定渲染形状为模型渲染
     */
    @Override
    @Nonnull
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    /**
     * 设置方块为半透明以支持火焰效果
     */
    @Override
    public boolean propagatesSkylightDown(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        return true;
    }

    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return getStateWithConnections(context.getLevel(), context.getClickedPos());
    }

    /**
     * 根据相邻方块计算连接状态
     *
     * @param level 世界
     * @param pos   方块位置
     * @return 带连接信息的方块状态
     */
    private BlockState getStateWithConnections(@Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        BlockState state = this.defaultBlockState();

        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue;
            }

            BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
            if (property != null) {
                BlockPos adjacentPos = pos.relative(direction);
                BlockState adjacentState = level.getBlockState(adjacentPos);

                boolean shouldConnect = !adjacentState.isAir() && adjacentState.isSolid();
                state = state.setValue(property, shouldConnect);
            }
        }

        return state;
    }

    @Override
    @Nonnull
    public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction direction,
                                  @Nonnull BlockState neighborState, @Nonnull LevelAccessor level,
                                  @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
        if (!this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        return getStateWithConnections(level, pos);
    }

    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public void tick(@Nonnull BlockState state, @Nonnull ServerLevel level,
                     @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        // 不执行火焰蔓延逻辑
    }

    @Override
    public void attack(@Nonnull BlockState state, @Nonnull Level level,
                       @Nonnull BlockPos pos, @Nonnull Player player) {
        if (!level.isClientSide()) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    protected boolean canBurn(@Nonnull BlockState state) {
        return false;
    }

    /**
     * 内部工具类，模拟 net.minecraft.Util.make 的功能
     */
    private static class Util {
        public static <T> T make(T object, java.util.function.Consumer<T> consumer) {
            consumer.accept(object);
            return object;
        }
    }
}
