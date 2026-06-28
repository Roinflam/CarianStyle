package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;
import pers.roinflam.carianstyle.visual.StackHudManager;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 叠层显示同步包（S2C，服务端 -> 客户端）。
 * <p>
 * 携带“本地玩家当前的全部叠层”的全量快照：一组 (serialId, 层数, 上限, 是否冷却)。
 * 用全量而非增量的原因：单个玩家的叠层种类很少（个位数），全量更简单、更鲁棒；
 * 且服务端已做差量判断（无变化不发包），带宽可忽略。
 * <p>
 * 上限随包下发的原因：部分附魔上限是动态的（随等级变化），客户端无法静态推断，
 * 需服务端按当前玩家状态算好再下发。
 * <p>
 * <b>冷却标志：</b>冷却倒计时项复用本包，额外携带一个 boolean——true 时该项的
 * (count, max) 表示 (剩余冷却 tick, 总冷却 tick)，客户端 HUD 据此切换为「剩余秒数 + 充能条」显示。
 *
 * @author FlameForge
 */
public class StackDisplayPacket {

    /** serialId -> (层数 / 剩余冷却, 上限 / 总冷却, 是否冷却) */
    private final Map<Integer, StackDisplayRegistry.Stacks> stacks;

    /**
     * @param stacks serialId -> Stacks 的映射
     */
    public StackDisplayPacket(Map<Integer, StackDisplayRegistry.Stacks> stacks) {
        this.stacks = stacks;
    }

    /**
     * 编码。
     *
     * @param packet 待编码包
     * @param buf    目标缓冲
     */
    public static void encode(StackDisplayPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.stacks.size());
        for (Map.Entry<Integer, StackDisplayRegistry.Stacks> e : packet.stacks.entrySet()) {
            StackDisplayRegistry.Stacks s = e.getValue();
            buf.writeVarInt(e.getKey());
            buf.writeVarInt(s.count());
            buf.writeVarInt(Math.max(0, s.max()));
            buf.writeBoolean(s.cooldown());
        }
    }

    /**
     * 解码。
     *
     * @param buf 源缓冲
     * @return 解码出的包
     */
    public static StackDisplayPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        Map<Integer, StackDisplayRegistry.Stacks> map = new HashMap<>(Math.max(4, size * 2));
        for (int i = 0; i < size; i++) {
            int serialId = buf.readVarInt();
            int count = buf.readVarInt();
            int max = buf.readVarInt();
            boolean cooldown = buf.readBoolean();
            map.put(serialId, new StackDisplayRegistry.Stacks(count, max, cooldown));
        }
        return new StackDisplayPacket(map);
    }

    /**
     * 处理（仅客户端执行：本包为 S2C）。
     *
     * @param packet 收到的包
     * @param ctx    网络上下文
     */
    public static void handle(StackDisplayPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> StackHudManager.accept(packet.stacks));
        ctx.get().setPacketHandled(true);
    }
}
