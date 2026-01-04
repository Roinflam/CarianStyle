package pers.roinflam.carianstyle.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 移动光源方块实体
 * <p>
 * 用于实现跟随实体移动的动态光源效果
 * 存在超过1tick后自动消失
 * </p>
 */
public class MoveLight extends TileEntity implements ITickable {

    /** 方块实体注册ID */
    public static final String ID = Reference.MOD_ID + ":move_light";

    /** 已存在的tick数 */
    private int existedTick = 0;

    @Override
    public void update() {
        existedTick++;

        // 存在超过1tick后移除
        if (existedTick > 1) {
            if (world != null && world.getBlockState(pos).getBlock() instanceof HideLight) {
                world.setBlockToAir(pos);
                world.removeTileEntity(pos);
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
    }
}