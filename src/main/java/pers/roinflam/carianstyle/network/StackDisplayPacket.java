package pers.roinflam.carianstyle.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import pers.roinflam.carianstyle.mc112.util.Sides;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;
import pers.roinflam.carianstyle.visual.StackHudManager;

import java.util.HashMap;
import java.util.Map;

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
 * <h3>v1.1：解码期长度校验 + 修复容量整数溢出</h3>
 * <p>
 * 原 {@link #decode} 有两个问题，第二个更隐蔽：
 * </p>
 * <ol>
 *   <li><b>长度无上限。</b>{@code size} 直接来自字节流，用于驱动读取循环。</li>
 *   <li><b>{@code new HashMap&lt;&gt;(Math.max(4, size * 2))} 会整数溢出。</b>
 *       当 {@code size} 大于 {@code 2^30} 时，{@code size * 2} 溢出成<b>负数</b>，
 *       {@code Math.max(4, 负数)} 得到 4，看似安全；但当 {@code size} 落在
 *       {@code 2^29 ~ 2^30} 之间时 {@code size * 2} 仍为正的十亿级，
 *       {@code HashMap} 会立刻按这个容量分配桶数组并 OOM。
 *       也就是说这个 {@code Math.max} 只在极端值下「碰巧」安全，
 *       在中间区段反而毫无防护。</li>
 * </ol>
 * <p>
 * 现在长度经 {@link PacketGuard#readBoundedVarIntSize} 校验（硬上限
 * {@value #MAX_ENTRIES} + 缓冲剩余字节交叉验证），容量表达式改用 {@code Math.min} 兜住，
 * 从根上不可能溢出。
 * </p>
 * <p>
 * 上限取 {@value #MAX_ENTRIES}：叠层项由本模组的 {@code StackDisplayRegistry} 静态注册，
 * 数量是编译期确定的个位数到几十，128 已是数倍余量。
 * </p>
 * <p>
 * <b>线格式没有变化</b>，新旧端可互通。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
public class StackDisplayPacket implements IMessage {

    /** 在 visual 通道里的包编号（只追加、永不复用） */
    public static final int ID = 0;

    /**
     * 单个包允许携带的最大叠层项数。
     * <p>见类注释：实际注册的叠层项远少于此值。</p>
     */
    private static final int MAX_ENTRIES = 128;

    /**
     * 每个条目在缓冲里至少占用的字节数。
     * <p>三个 VarInt 至少各 1 字节 + 1 个 boolean 字节 = 4。取最小值是为了
     * 让交叉校验保持保守——宁可放过一个偏小的坏包，也不能误杀正常包。</p>
     */
    private static final int MIN_BYTES_PER_ENTRY = 4;

    /** serialId -> (层数 / 剩余冷却, 上限 / 总冷却, 是否冷却) */
    private Map<Integer, StackDisplayRegistry.Stacks> stacks;

    /** 坏包标记：解码失败的包由处理器直接丢掉 */
    private boolean valid = true;

    /** 1.12.2 收包时先用无参构造器建实例，再 {@link #fromBytes} 填字段 */
    public StackDisplayPacket() {
        this.stacks = new HashMap<>();
    }

    /**
     * @param stacks serialId -> Stacks 的映射
     */
    public StackDisplayPacket(Map<Integer, StackDisplayRegistry.Stacks> stacks) {
        this.stacks = stacks;
    }

    /**
     * 编码。
     *
     * @param buf 目标缓冲
     */
    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(stacks.size());
        for (Map.Entry<Integer, StackDisplayRegistry.Stacks> e : stacks.entrySet()) {
            StackDisplayRegistry.Stacks s = e.getValue();
            out.writeVarInt(e.getKey());
            out.writeVarInt(s.count());
            out.writeVarInt(Math.max(0, s.max()));
            out.writeBoolean(s.cooldown());
        }
    }

    /**
     * 解码。
     * <p>v1.1：长度经 {@link PacketGuard} 校验，容量计算不再可能溢出，详见类注释。</p>
     *
     * @param buf 源缓冲
     */
    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            PacketBuffer in = new PacketBuffer(buf);
            int size = PacketGuard.readBoundedVarIntSize(buf, MAX_ENTRIES, MIN_BYTES_PER_ENTRY,
                    "StackDisplayPacket");
            // size 已被限制在 [0, MAX_ENTRIES]，此处的 *2 不可能溢出；
            // 仍显式用 min 兜一层，避免将来有人调大 MAX_ENTRIES 时重新引入溢出
            int capacity = Math.max(4, Math.min(MAX_ENTRIES * 2, size * 2));
            Map<Integer, StackDisplayRegistry.Stacks> map = new HashMap<>(capacity);
            for (int i = 0; i < size; i++) {
                int serialId = in.readVarInt();
                int count = in.readVarInt();
                int max = in.readVarInt();
                boolean cooldown = in.readBoolean();
                map.put(serialId, new StackDisplayRegistry.Stacks(count, max, cooldown));
            }
            stacks = map;
        } catch (RuntimeException e) {
            valid = false;
            PacketGuard.reject("StackDisplayPacket", e);
        }
    }

    /**
     * 处理（仅客户端：本包只注册在客户端一侧）。
     */
    public static class Handler implements IMessageHandler<StackDisplayPacket, IMessage> {
        @Override
        public IMessage onMessage(StackDisplayPacket packet, MessageContext ctx) {
            if (packet.valid) {
                Sides.runOnWorldThread(ctx, () -> StackHudManager.accept(packet.stacks));
            }
            return null;
        }
    }
}
