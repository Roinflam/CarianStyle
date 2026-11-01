package pers.roinflam.carianstyle.mc112.util;

import net.minecraft.world.World;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import javax.annotation.Nonnull;

/**
 * 逻辑侧与线程的小工具
 */
public final class Sides {

    private Sides() {
    }

    /**
     * 是否客户端世界（{@code world.isRemote}），对应 1.20.1 的 {@code level.isClientSide}
     */
    public static boolean isClient(@Nonnull World world) {
        return world.isRemote;
    }

    /**
     * 把网络包的处理挪到世界线程上执行
     * <p>
     * 1.12.2 的 {@code IMessageHandler.onMessage} 在网络线程上调用，碰世界、实体、渲染数据前要切回主线程。
     * {@code getWorldThread} 按收包的一端返回客户端的 {@code Minecraft} 或服务端的 {@code MinecraftServer}。
     * </p>
     *
     * @param ctx  收包上下文
     * @param task 要在世界线程上跑的任务
     */
    public static void runOnWorldThread(@Nonnull MessageContext ctx, @Nonnull Runnable task) {
        FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(task);
    }
}
