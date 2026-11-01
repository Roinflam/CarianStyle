package pers.roinflam.carianstyle.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import pers.roinflam.carianstyle.mc112.util.Sides;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享数值下发包（S2C，服务端 -&gt; 客户端）。
 *
 * <h3>要解决的问题</h3>
 * <p>
 * {@code config/carianstyle/enchantment_values.json} 是<b>各端各一份</b>的：
 * 多人游戏时客户端读的是玩家自己电脑上那份，不是服务器那份。
 * </p>
 * <p>
 * 绝大多数数值只在服务端参与判定，这没问题。但有两个值客户端也要读：
 * </p>
 * <ul>
 *   <li>{@code gravitas.field_radius} —— {@code GravitasDistortionRenderer} 据此画地面范围圈；</li>
 *   <li>{@code concealing_veil.battle_duration} —— {@code CarianStyleConditionDisplay} 据此画倒计时进度条。</li>
 * </ul>
 * <p>
 * 服主把力场半径从 12 改成 20，服务端按 20 判定、客户端按本地默认 12 画圈，
 * 玩家就会看到<b>圈外的人却被打中</b>。这正是这两个附魔的原注释里
 * 明确写着「范围判定与视觉共用同一个常量，避免两处对不上」想要避免的情况——
 * 数值一旦可配置，「共用一个常量」这个前提就不再成立了。
 * </p>
 *
 * <h3>为什么只发一小部分</h3>
 * <p>
 * 全部 400 多项数值一起发大约 15 KB，登录时发一次其实也能接受。
 * 但绝大多数键客户端根本不会读，发过去只是徒增一份需要维护一致性的双端状态。
 * 所以改为<b>在声明处显式标记</b>：只有用
 * {@link EnchantmentValues#defineShared} 声明的数值才会进这个包。
 * </p>
 * <p>
 * 这比在某个远处维护一张白名单好：加新的客户端可见数值时，
 * 「要不要同步」这个决定就写在数值声明的那一行，不会漏。
 * </p>
 *
 * <h3>时机</h3>
 * <p>
 * 玩家登录时发一次（见 {@code ValueSyncHandler}）。数值在服务器运行期间
 * 只会因为管理员手动重载而变化，不需要持续同步。
 * 客户端断开连接时清空覆盖，回到单人游戏自动恢复读本地文件。
 * </p>
 *
 * <h3>解码校验</h3>
 * <p>
 * 条目数有硬上限并与缓冲剩余字节交叉验证；键长度受限；
 * 值本身的范围校验交给 {@link EnchantmentValues#applySynced}——
 * 范围是本端代码定义的语义边界，不该由对端决定。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
public class EnchantmentValuesPacket implements IMessage {

    /** 在 main 通道里的包编号（只追加、永不复用） */
    public static final int ID = 1;

    /**
     * 单个包允许携带的最大条目数。
     * <p>共享数值是显式标记的，目前只有 2 项；256 是数量级的余量。</p>
     */
    private static final int MAX_ENTRIES = 256;

    /** 单个键的最大长度（复合键形如 {@code gravitas.field_radius}） */
    private static final int MAX_KEY_LENGTH = 128;

    /**
     * 每个条目在缓冲里至少占用的字节数。
     * <p>键至少 1 字节长度前缀 + 1 字节内容，值 8 字节 double，合计 10。
     * 取最小值让交叉校验保持保守——宁可放过一个偏小的坏包，也不能误杀正常包。</p>
     */
    private static final int MIN_BYTES_PER_ENTRY = 10;

    /** 复合键 -> 值 */
    private Map<String, Double> values;

    /** 坏包标记：解码失败的包由处理器直接丢掉 */
    private boolean valid = true;

    /** 1.12.2 收包时先用无参构造器建实例，再 {@link #fromBytes} 填字段 */
    public EnchantmentValuesPacket() {
        this.values = new HashMap<>();
    }

    /**
     * @param values 复合键到值的映射
     */
    public EnchantmentValuesPacket(Map<String, Double> values) {
        this.values = values;
    }

    /**
     * 按当前服务端状态构造一个下发包。
     *
     * @return 包含全部共享数值的包
     */
    public static EnchantmentValuesPacket ofCurrent() {
        List<EnchantmentValues.Handle> shared = EnchantmentValues.sharedHandles();
        Map<String, Double> map = new HashMap<>(Math.max(4, shared.size() * 2));
        for (EnchantmentValues.Handle handle : shared) {
            map.put(handle.getCompoundKey(), handle.get());
        }
        return new EnchantmentValuesPacket(map);
    }

    /**
     * 编码。
     *
     * @param buf 目标缓冲
     */
    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer out = new PacketBuffer(buf);
        out.writeVarInt(values.size());
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            out.writeString(entry.getKey());
            out.writeDouble(entry.getValue());
        }
    }

    /**
     * 解码。
     *
     * @param buf 源缓冲
     */
    @Override
    public void fromBytes(ByteBuf buf) {
        try {
            PacketBuffer in = new PacketBuffer(buf);
            int size = PacketGuard.readBoundedVarIntSize(buf, MAX_ENTRIES, MIN_BYTES_PER_ENTRY,
                    "EnchantmentValuesPacket");
            Map<String, Double> map = new HashMap<>(Math.max(4, Math.min(MAX_ENTRIES * 2, size * 2)));
            for (int i = 0; i < size; i++) {
                String key = in.readString(MAX_KEY_LENGTH);
                map.put(key, in.readDouble());
            }
            values = map;
        } catch (RuntimeException e) {
            valid = false;
            PacketGuard.reject("EnchantmentValuesPacket", e);
        }
    }

    /**
     * 处理（仅客户端：本包只注册在客户端一侧）。
     * <p>
     * {@link EnchantmentValues#applySynced} 不引用任何客户端专有类，双端加载安全。
     * 1.12.2 的处理器在网络线程上调用，切回世界线程再写覆盖值。
     * </p>
     */
    public static class Handler implements IMessageHandler<EnchantmentValuesPacket, IMessage> {
        @Override
        public IMessage onMessage(EnchantmentValuesPacket packet, MessageContext ctx) {
            if (packet.valid) {
                Sides.runOnWorldThread(ctx, () -> EnchantmentValues.applySynced(packet.values));
            }
            return null;
        }
    }
}
