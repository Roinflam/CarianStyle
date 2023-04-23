package pers.roinflam.carianstyle.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ITickable;
import pers.roinflam.carianstyle.block.light.HideLight;
import pers.roinflam.carianstyle.utils.Reference;

public class MoveLight extends TileEntity implements ITickable {
    public static final String ID = Reference.MOD_ID + ":move_light";
    private int existedTick = 0;

    @Override
    public void update() {
        if (existedTick++ > 1) {
            if (world.getBlockState(pos).getBlock() instanceof HideLight) {
                world.setBlockToAir(pos);
                world.removeTileEntity(pos);
            }
        }
    }

    public void retime() {
        existedTick = 0;
    }
}
