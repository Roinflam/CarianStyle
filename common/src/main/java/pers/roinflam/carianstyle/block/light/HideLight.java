package pers.roinflam.carianstyle.block.light;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import pers.roinflam.carianstyle.init.CarianStyleBlockEntities;
import pers.roinflam.carianstyle.tileentity.MoveLight;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 隐藏光源方块
 * <p>
 * 用于实现动态光源效果的透明发光方块
 * 特性：
 * - 外观为空气（不可见）
 * - 亮度由 {@link #LEVEL} 决定（默认 15）
 * - 不可被其他方块替换
 * - 配合MoveLight实体自动消失
 * </p>
 */
public class HideLight extends AirBlock implements EntityBlock {

    /** 光照等级，方块属性里用 {@code lightLevel} 读它 */
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL;

    /** 按属性重建本方块的编解码器（见 {@link #codec()}） */
    private static final MapCodec<HideLight> CODEC = simpleCodec(HideLight::new);

    /**
     * 构造函数
     *
     * @param properties 方块属性（应包含光照等级设置）
     */
    public HideLight(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LEVEL, 15));
    }

    /**
     * 1.21.1 的方块都要提供编解码器。父类 {@link AirBlock#codec()} 声明的返回类型是 {@code MapCodec<AirBlock>}，
     * 泛型不协变，覆写只能保持这个返回类型；不覆写的话按父类的编解码器重建出来的是普通空气方块，丢了本类。
     * 所以返回本类的编解码器，做一次未检查转换（{@code MapCodec} 只用来按属性重建方块，类型擦除后是同一个对象）。
     */
    @Override
    @SuppressWarnings("unchecked")
    public MapCodec<AirBlock> codec() {
        return (MapCodec<AirBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEVEL);
    }

    /**
     * 判断方块是否可被替换
     *
     * @param state 方块状态
     * @param context 方块放置上下文
     * @return 始终返回 false，防止被其他方块替换
     */
    @Override
    public boolean canBeReplaced(@Nonnull BlockState state, @Nonnull BlockPlaceContext context) {
        return false;
    }

    /**
     * 创建方块实体
     *
     * @param pos 方块位置
     * @param state 方块状态
     * @return MoveLight 方块实体实例
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new MoveLight(pos, state);
    }

    /**
     * 获取方块实体的 Ticker（1.20.1 新增）
     * <p>
     * 用于注册方块实体的 tick 方法
     * </p>
     *
     * @param level 世界
     * @param state 方块状态
     * @param type 方块实体类型
     * @return BlockEntityTicker 实例，如果类型匹配则返回 MoveLight::tick
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            @Nonnull Level level,
            @Nonnull BlockState state,
            @Nonnull BlockEntityType<T> type) {

        // 只在服务端执行 tick
        if (!level.isClientSide()) {
            // 检查类型是否匹配
            return type == CarianStyleBlockEntities.MOVE_LIGHT.get()
                    ? (BlockEntityTicker<T>) (BlockEntityTicker<MoveLight>) MoveLight::tick
                    : null;
        }
        return null;
    }
}