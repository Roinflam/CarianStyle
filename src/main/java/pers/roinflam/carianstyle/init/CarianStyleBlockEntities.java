package pers.roinflam.carianstyle.init;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.GameRegistry;
import pers.roinflam.carianstyle.tileentity.MoveLight;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 模组方块实体注册类
 * <p>
 * 1.12.2 版：方块实体没有注册表，也没有 {@code BlockEntityType}，preInit 里由主类调 {@link #register()}，
 * 用 {@code GameRegistry.registerTileEntity} 登记类和存档 id。存档 id 和以前的版本一样是 {@code carianstyle:move_light}。
 * </p>
 */
public class CarianStyleBlockEntities {

    /**
     * 移动光源方块实体的存档 id
     */
    public static final ResourceLocation MOVE_LIGHT = new ResourceLocation(Reference.MOD_ID, "move_light");

    private static boolean registered = false;

    /**
     * 登记全部方块实体（preInit，重复调用安全）
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        GameRegistry.registerTileEntity(MoveLight.class, MOVE_LIGHT);
    }
}
