package pers.roinflam.carianstyle.network;

import net.minecraft.network.FriendlyByteBuf;

import javax.annotation.Nonnull;

/**
 * 数据包解码期的输入校验工具。
 *
 * <h3>为什么解码期就要校验</h3>
 * <p>
 * 解码方法是<b>唯一</b>一个「外部字节流第一次变成 Java 对象」的地方。
 * 在这里放过的坏数据，后面每一个使用点都得各自防一遍，迟早漏一个。
 * </p>
 * <p>
 * 具体到本模组，最危险的模式是「先读一个长度、再按这个长度分配容器」：
 * </p>
 * <pre>
 * int size = buf.readInt();                 // 攻击者可控
 * List&lt;Integer&gt; ids = new ArrayList&lt;&gt;(size); // size = 2^31-1 时当场 OOM
 * </pre>
 * <p>
 * 注意这不需要恶意攻击也会发生——网络层的一个位翻转、或双端包 ID 因版本不一致而错位，
 * 都会让某段无关字节被当成长度读出来。{@link #readBoundedSize} 就是为这个场景准备的：
 * 除了硬上限，还会拿缓冲区里<b>实际剩余的字节数</b>做二次校验——
 * 声称有 1000 个元素但缓冲里只剩 12 字节，这个包必然是坏的。
 * </p>
 *
 * <h3>校验失败为什么直接抛异常</h3>
 * <p>
 * Forge 的 {@code SimpleChannel} 会捕获解码异常并断开该连接，这正是我们想要的：
 * 坏包来自哪一端就断哪一端，不会污染其余玩家。返回一个「安全的空包」反而更糟——
 * 问题被静默吞掉，日后排查时毫无线索。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class PacketGuard {

    private PacketGuard() {
    }

    /**
     * 读取一个「元素个数」并做双重校验。
     *
     * @param buf              源缓冲
     * @param size             已经读出来的长度值
     * @param maxSize          业务上允许的最大元素数
     * @param bytesPerElement  每个元素在缓冲里至少占多少字节（用于对照剩余可读字节）
     * @param packetName       包名，仅用于异常信息
     * @return 校验通过的长度
     * @throws IllegalArgumentException 长度为负、超出上限，或与剩余字节数明显矛盾
     */
    public static int checkSize(@Nonnull FriendlyByteBuf buf, int size, int maxSize,
                                int bytesPerElement, @Nonnull String packetName) {
        if (size < 0 || size > maxSize) {
            throw new IllegalArgumentException(
                    packetName + " 的元素个数非法：" + size + "（允许范围 0 ~ " + maxSize + "）");
        }
        // 二次校验：声称的元素数所需的字节，不可能超过缓冲里实际剩余的字节
        long required = (long) size * bytesPerElement;
        if (required > buf.readableBytes()) {
            throw new IllegalArgumentException(
                    packetName + " 声称有 " + size + " 个元素（需 " + required
                            + " 字节），但缓冲区只剩 " + buf.readableBytes() + " 字节");
        }
        return size;
    }

    /**
     * 读取一个定长 int 长度并校验。
     *
     * @param buf             源缓冲
     * @param maxSize         业务上允许的最大元素数
     * @param bytesPerElement 每个元素至少占用的字节数
     * @param packetName      包名，仅用于异常信息
     * @return 校验通过的长度
     */
    public static int readBoundedSize(@Nonnull FriendlyByteBuf buf, int maxSize,
                                      int bytesPerElement, @Nonnull String packetName) {
        return checkSize(buf, buf.readInt(), maxSize, bytesPerElement, packetName);
    }

    /**
     * 读取一个 VarInt 长度并校验。
     *
     * @param buf             源缓冲
     * @param maxSize         业务上允许的最大元素数
     * @param bytesPerElement 每个元素至少占用的字节数
     * @param packetName      包名，仅用于异常信息
     * @return 校验通过的长度
     */
    public static int readBoundedVarIntSize(@Nonnull FriendlyByteBuf buf, int maxSize,
                                            int bytesPerElement, @Nonnull String packetName) {
        return checkSize(buf, buf.readVarInt(), maxSize, bytesPerElement, packetName);
    }

    /**
     * 把一个浮点数收拢到合法区间，并剔除 NaN / 无穷大。
     * <p>
     * 渲染器拿到 NaN 半径不会崩，但会画出整屏乱纹或者什么都不画，
     * 而且这种问题极难反查到「某个包里的一个浮点数」上，所以在入口挡掉。
     * </p>
     *
     * @param value        原始值
     * @param min          下界
     * @param max          上界
     * @param fallback     值为 NaN / 无穷大时的回退值
     * @return 收拢后的值
     */
    public static float sanitize(float value, float min, float max, float fallback) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return fallback;
        }
        return Math.min(max, Math.max(min, value));
    }

    /**
     * 把一个双精度坐标收拢到世界的合法范围，并剔除 NaN / 无穷大。
     * <p>
     * 原版世界边界最大 ±3000 万格，这里用 ±3200 万留一点余量。
     * 超出该范围的坐标不可能来自正常游戏，通常意味着包已经错位。
     * </p>
     *
     * @param value 原始坐标
     * @return 收拢后的坐标；NaN / 无穷大回退为 0
     */
    public static double sanitizeCoordinate(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.min(32_000_000.0D, Math.max(-32_000_000.0D, value));
    }

    /**
     * 把一个整数收拢到合法区间。
     *
     * @param value 原始值
     * @param min   下界
     * @param max   上界
     * @return 收拢后的值
     */
    public static int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }
}
