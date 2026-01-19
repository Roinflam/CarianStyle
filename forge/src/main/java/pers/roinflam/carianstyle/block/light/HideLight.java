package pers.roinflam.carianstyle.block.light;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
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
 * - 发出最大亮度的光
 * - 不可被其他方块替换
 * - 配合MoveLight实体自动消失
 * </p>
 */
public class HideLight extends AirBlock implements EntityBlock {

    /**
     * 构造函数
     *
     * @param properties 方块属性（应包含光照等级设置）
     */
    public HideLight(BlockBehaviour.Properties properties) {
        super(properties);
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