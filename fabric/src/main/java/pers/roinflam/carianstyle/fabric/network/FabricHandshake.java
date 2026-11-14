package pers.roinflam.carianstyle.fabric.network;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import pers.roinflam.carianstyle.network.CarianNetwork;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.util.function.Consumer;

/**
 * 进服时的版本握手（配置阶段）用的两个包和服务端任务。
 * <p>
 * NeoForge 在配置阶段按每个包登记的版本号自动核对，对不上就拒绝连接；Fabric 没有这一步，
 * 没装本模组的客户端照样能进服（注册表同步对没装的客户端直接跳过），进来后碰到本模组的方块、物品就出错，
 * 隐身之类在客户端执行的效果也不起作用。所以 Fabric 上自己握手：服务端在配置阶段发 {@link Hello}，
 * 客户端回 {@link Reply}，两组版本号都和服务端一致才放行。
 * </p>
 * <p>
 * 版本号就是 {@link CarianNetwork#MAIN_VERSION}、{@link CarianNetwork#VISUAL_VERSION}，和 NeoForge 上两组包登记的版本号是同一对。
 * </p>
 */
public final class FabricHandshake {

    private FabricHandshake() {
    }

    /** 服务端发给客户端：服务端的两组版本号。 */
    public record Hello(String main, String visual) implements CustomPacketPayload {

        public static final Type<Hello> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "handshake"));

        public static final StreamCodec<ByteBuf, Hello> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Hello::main,
                ByteBufCodecs.STRING_UTF8, Hello::visual,
                Hello::new);

        /** 本端的两组版本号 */
        @Nonnull
        public static Hello current() {
            return new Hello(CarianNetwork.MAIN_VERSION, CarianNetwork.VISUAL_VERSION);
        }

        @Override
        @Nonnull
        public Type<Hello> type() {
            return TYPE;
        }
    }

    /** 客户端回给服务端：客户端的两组版本号。 */
    public record Reply(String main, String visual) implements CustomPacketPayload {

        public static final Type<Reply> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "handshake_reply"));

        public static final StreamCodec<ByteBuf, Reply> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Reply::main,
                ByteBufCodecs.STRING_UTF8, Reply::visual,
                Reply::new);

        /** 本端的两组版本号 */
        @Nonnull
        public static Reply current() {
            return new Reply(CarianNetwork.MAIN_VERSION, CarianNetwork.VISUAL_VERSION);
        }

        /** 两组版本号是否都和本端一致 */
        public boolean matchesCurrent() {
            return CarianNetwork.MAIN_VERSION.equals(main) && CarianNetwork.VISUAL_VERSION.equals(visual);
        }

        @Override
        @Nonnull
        public Type<Reply> type() {
            return TYPE;
        }
    }

    /**
     * 配置阶段的服务端任务：开始时发 {@link Hello}，收到合格的 {@link Reply} 才完成，
     * 任务没完成之前客户端停在配置阶段，进不了世界。
     */
    public record Task() implements ConfigurationTask {

        public static final ConfigurationTask.Type TYPE = new ConfigurationTask.Type(Reference.MOD_ID + ":handshake");

        @Override
        public void start(@Nonnull Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createS2CPacket(Hello.current()));
        }

        @Override
        @Nonnull
        public ConfigurationTask.Type type() {
            return TYPE;
        }
    }
}
