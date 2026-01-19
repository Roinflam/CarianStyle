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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 自定义火焰方块基类
 * <p>
 * 用于创建不会自然蔓延的装饰性火焰方块
 * 支持多方向显示，点击即可熄灭
 * </p>
 */
public abstract class FireBase extends BaseFireBlock {

    /**
     * 方向属性：北、东、南、西、上
     * <p>
     * 用于控制火焰在不同方向上的显示
     * </p>
     */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;

    /**
     * 方向属性映射表
     */
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

    /**
     * 碰撞箱
     */
    private static final VoxelShape DOWN_AABB = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    /**
     * 构造函数
     *
     * @param properties 方块属性
     */
    public FireBase(BlockBehaviour.Properties properties) {
        super(properties, 1.0F);

        // 注册默认状态：所有方向都为 false
        this.registerDefaultState(
                this.stateDefinition.any()
                        .setValue(NORTH, false)
                        .setValue(EAST, false)
                        .setValue(SOUTH, false)
                        .setValue(WEST, false)
                        .setValue(UP, false)
        );
    }

    /**
     * 创建方块状态定义
     * <p>
     * 注册所有方向属性
     * </p>
     *
     * @param builder 状态定义构建器
     */
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP);
    }

    /**
     * 获取方块的碰撞箱
     *
     * @param state 方块状态
     * @param level 世界
     * @param pos 位置
     * @param context 碰撞上下文
     * @return 碰撞箱形状
     */
    @Override
    @Nonnull
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level,
                               @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return DOWN_AABB;
    }

    /**
     * 放置方块时获取方块状态
     * <p>
     * 根据周围方块设置方向属性
     * </p>
     *
     * @param context 放置上下文
     * @return 方块状态
     */
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return getStateWithConnections(context.getLevel(), context.getClickedPos());
    }

    /**
     * 根据周围方块更新连接状态
     *
     * @param level 世界
     * @param pos 位置
     * @return 更新后的方块状态
     */
    private BlockState getStateWithConnections(@Nonnull BlockGetter level, @Nonnull BlockPos pos) {
        BlockState state = this.defaultBlockState();

        // 检查每个方向
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) {
                continue; // 火焰没有下方连接
            }

            BooleanProperty property = PROPERTY_BY_DIRECTION.get(direction);
            if (property != null) {
                BlockPos adjacentPos = pos.relative(direction);
                BlockState adjacentState = level.getBlockState(adjacentPos);

                // 如果相邻方块不是空气，则显示该方向的火焰
                boolean shouldConnect = !adjacentState.isAir() &&
                        adjacentState.isSolid();
                state = state.setValue(property, shouldConnect);
            }
        }

        return state;
    }

    /**
     * 方块状态更新时调用
     * <p>
     * 当周围方块变化时，更新火焰的连接状态
     * </p>
     *
     * @param state 当前方块状态
     * @param direction 更新方向
     * @param neighborState 邻居方块状态
     * @param level 世界
     * @param pos 当前位置
     * @param neighborPos 邻居位置
     * @return 更新后的方块状态
     */
    @Override
    @Nonnull
    public BlockState updateShape(@Nonnull BlockState state, @Nonnull Direction direction,
                                  @Nonnull BlockState neighborState, @Nonnull LevelAccessor level,
                                  @Nonnull BlockPos pos, @Nonnull BlockPos neighborPos) {
        // 如果下方没有支撑，移除火焰
        if (!this.canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }

        // 更新连接状态
        return getStateWithConnections(level, pos);
    }

    /**
     * 检查火焰是否可以存活
     *
     * @param state 方块状态
     * @param level 世界
     * @param pos 位置
     * @return 如果可以存活返回 true
     */
    @Override
    public boolean canSurvive(@Nonnull BlockState state, @Nonnull LevelReader level, @Nonnull BlockPos pos) {
        // 检查下方是否有支撑方块
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        return belowState.isFaceSturdy(level, below, Direction.UP);
    }

    /**
     * 方块 tick 更新
     * <p>
     * 覆盖此方法以阻止火焰自然蔓延
     * </p>
     *
     * @param state 方块状态
     * @param level 服务端世界
     * @param pos 方块位置
     * @param random 随机源
     */
    @Override
    public void tick(@Nonnull BlockState state, @Nonnull ServerLevel level,
                     @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        // 不执行火焰蔓延逻辑，火焰保持静态
    }

    /**
     * 玩家攻击（左键点击）方块时调用
     * <p>
     * 实现点击熄灭火焰的功能
     * </p>
     *
     * @param state 方块状态
     * @param level 世界
     * @param pos 方块位置
     * @param player 玩家
     */
    @Override
    public void attack(@Nonnull BlockState state, @Nonnull Level level,
                       @Nonnull BlockPos pos, @Nonnull Player player) {
        // 点击熄灭火焰
        if (!level.isClientSide()) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * 判断方块状态是否可燃
     * <p>
     * 返回 false 使火焰不会燃烧其他方块
     * </p>
     *
     * @param state 方块状态
     * @return false
     */
    @Override
    protected boolean canBurn(@Nonnull BlockState state) {
        return false;
    }

    /**
     * 工具类：创建不可变映射的辅助方法
     */
    private static class Util {
        public static <T> T make(T object, java.util.function.Consumer<T> consumer) {
            consumer.accept(object);
            return object;
        }
    }
}