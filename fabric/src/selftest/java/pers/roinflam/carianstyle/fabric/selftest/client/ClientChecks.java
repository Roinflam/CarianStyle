package pers.roinflam.carianstyle.fabric.selftest.client;

import com.terraformersmc.modmenu.ModMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.config.ClothConfigScreen;
import pers.roinflam.carianstyle.fabric.network.FabricHandshake;

import java.util.concurrent.CompletableFuture;

/**
 * 开发客户端上给 TestHelper 的 {@code call} 用的检查入口，只在开发运行时的类路径上，不进 jar。
 */
public final class ClientChecks {

    private ClientChecks() {
    }

    // 单人世界里集成服和客户端同一个进程，直接读服务端的计数
    public static String causalityCount() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return "no player";
        }
        return String.valueOf(EnchantmentDataManager.getCounter("causality_principle", mc.player.getUUID()));
    }

    public static String openClothConfig() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = ClothConfigScreen.createConfigScreen(mc.screen);
        mc.setScreen(screen);
        return screen.getClass().getName();
    }

    // 模组列表里「配置」按钮走的就是这个查找（按 modmenu 入口找到本模组的工厂）
    public static String openModMenuConfig() {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = ModMenu.getConfigScreen("carianstyle", mc.screen);
        if (screen == null) {
            return "null";
        }
        mc.setScreen(screen);
        return screen.getClass().getName();
    }

    // 摘掉握手回包：服务端收到的是「没听懂」，和没装本模组的客户端一样
    public static String handshakeAsNotInstalled() {
        ClientLoginNetworking.unregisterGlobalReceiver(FabricHandshake.CHANNEL);
        return "unregistered";
    }

    public static String handshakeWithVersion(String version) {
        ClientLoginNetworking.unregisterGlobalReceiver(FabricHandshake.CHANNEL);
        ClientLoginNetworking.registerGlobalReceiver(FabricHandshake.CHANNEL, (client, handler, buf, listenerAdder) -> {
            FriendlyByteBuf reply = PacketByteBufs.create();
            new FabricHandshake.Versions(version, version).write(reply);
            return CompletableFuture.completedFuture(reply);
        });
        return "reply " + version;
    }

    public static String handshakeRestore() {
        ClientLoginNetworking.unregisterGlobalReceiver(FabricHandshake.CHANNEL);
        ClientLoginNetworking.registerGlobalReceiver(FabricHandshake.CHANNEL,
                (client, handler, buf, listenerAdder) -> CompletableFuture.completedFuture(FabricHandshake.currentVersions()));
        return "restored";
    }
}
