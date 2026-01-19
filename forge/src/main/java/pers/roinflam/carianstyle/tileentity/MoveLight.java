package pers.roinflam.carianstyle.tileentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.init.CarianStyleBlockEntities;

import javax.annotation.Nonnull;

/**
 * 移动光源方块实体
 * <p>
 * 用于实现跟随实体移动的动态光源效果
 * 存在超过1tick后自动消失
 * </p>
 */
public class MoveLight extends BlockEntity {

    /** 已存在的tick数 */
    private int existedTick = 0;

    /**
     * 构造函数
     *
     * @param pos 方块位置
     * @param state 方块状态
     */
    public MoveLight(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        // 使用新的方块实体注册引用
        super(CarianStyleBlockEntities.MOVE_LIGHT.get(), pos, state);
    }

    /**
     * 服务端 tick 方法
     * <p>
     * 1.20.1 中 ITickable 接口被移除，改为静态 tick 方法
     * 需要在方块的 getTicker 方法中返回此方法的引用
     * </p>
     *
     * @param level 世界
     * @param pos 方块位置
     * @param state 方块状态
     * @param blockEntity 方块实体实例
     */
    public static void tick(@Nonnull Level level, @Nonnull BlockPos pos,
                            @Nonnull BlockState state, @Nonnull MoveLight blockEntity) {
        blockEntity.existedTick++;

        // 存在超过1tick后移除
        if (blockEntity.existedTick > 1) {
            if (state.getBlock() instanceof HideLight) {
                level.removeBlock(pos, false);
                level.removeBlockEntity(pos);
            }
        }
    }

    /**
     * 重置计时器
     * <p>
     * 当光源需要继续存在时调用
     * </p>
     */
    public void retime() {
        existedTick = 0;
        // 标记为已修改，确保数据同步
        setChanged();
    }
}