package pers.roinflam.carianstyle.mc112.hook;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketUpdateHealth;
import net.minecraft.potion.Potion;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 本模组「每 tick 自我掉血」的效果不再让镜头一直晃（客户端）
 *
 * <h3>为什么要拦</h3>
 * <p>
 * 切腹、出血每 tick 都在服务端直接扣血，服务端就每 tick 发一个血量包。1.12.2 的
 * {@code EntityPlayerSP.setPlayerSPHealth} 收到「血量下降」时会设 {@code hurtResistantTime}、调一次
 * {@code damageEntity}，并把 {@code hurtTime = maxHurtTime = 10}——受伤晃动从头播。每 tick 重置一次，
 * 镜头就一直抽搐。1.20.1 用 mixin 在 {@code LocalPlayer.hurtTo} 开头取消；1.12.2 的
 * {@code EntityPlayerSP.damageEntity} 被覆写成直接 {@code setHealth}、不发 {@code LivingHurtEvent}，没有事件可拦。
 * </p>
 *
 * <h3>做法</h3>
 * <ul>
 *     <li>每次连上服务器（单人内置服的本地通道也一样）往网络管道的 {@code "packet_handler"}
 *         前面插一个只认 {@link SPacketUpdateHealth} 的入站处理器。<b>每次连接新建一个实例</b>，
 *         插入前先查有没有同名的：处理器没有 {@code @Sharable}，同一个实例第二次插入会抛异常，
 *         而这个事件在网络线程上触发，第二次进服就出事。</li>
 *     <li>收到血量包时吞掉原消息，把它投到主线程处理（原版处理每个包也是投到主线程，顺序不变）。
 *         主线程里：新血量低于当前血量、且本地玩家身上有切腹或出血，就只 {@code setHealth} 并照原版
 *         更新饥饿值和饱和度；否则原样交给 {@code handleUpdateHealth}。</li>
 *     <li>找不到 {@code "packet_handler"} 就不装，日志记一次。</li>
 * </ul>
 * <p>
 * 真正挨打时服务端会另发实体状态 2（受伤动画 + 音效），不走血量包，照常晃——和 1.20.1 只拦
 * {@code hurtTo} 的效果一致。
 * </p>
 * <p>
 * 本类引用 {@code init/CarianStylePotion} 取两个药水，不引用 {@code enchantment/}、{@code visual/client/} 的具体类。
 * </p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class HealthPacketHook {

    /**
     * 插进管道的处理器名
     */
    private static final String HANDLER_NAME = "carianstyle_health";

    /**
     * 原版处理游戏包的那个处理器名（{@code NetworkManager}）
     */
    private static final String VANILLA_HANDLER = "packet_handler";

    private static boolean warnedMissing = false;

    private HealthPacketHook() {
    }

    /**
     * 连上服务器：往管道里插血量包处理器
     *
     * @param event 客户端连上服务器事件（网络线程）
     */
    @SubscribeEvent
    public static void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ChannelPipeline pipeline = event.getManager().channel().pipeline();
        if (pipeline.get(HANDLER_NAME) != null) {
            return;
        }
        if (pipeline.get(VANILLA_HANDLER) == null) {
            if (!warnedMissing) {
                warnedMissing = true;
                LogUtil.warn("卡利亚式附魔 - 网络管道里没有 " + VANILLA_HANDLER + "，持续掉血时的受伤晃动无法屏蔽");
            }
            return;
        }
        try {
            pipeline.addBefore(VANILLA_HANDLER, HANDLER_NAME, new HealthHandler(event.getManager()));
        } catch (RuntimeException e) {
            // 和别的处理器撞名、或者管道正在关闭：不装，不影响原版处理
            LogUtil.warn("卡利亚式附魔 - 插入血量包处理器失败：" + e);
        }
    }

    /**
     * 判断本地玩家是否处于本模组「每 tick 自我掉血」的状态（切腹、出血）。
     * <p>将来新增同类「每 tick 直扣血量」的效果，在此追加即可。</p>
     *
     * @param player 本地玩家
     * @return 处于任一持续掉血状态返回 true
     */
    private static boolean hasContinuousDrainEffect(@Nonnull EntityPlayerSP player) {
        return hasEffect(player, CarianStylePotion.INCISION.isPresent() ? CarianStylePotion.INCISION.get() : null)
                || hasEffect(player, CarianStylePotion.HEMORRHAGE.isPresent() ? CarianStylePotion.HEMORRHAGE.get() : null);
    }

    private static boolean hasEffect(@Nonnull EntityPlayerSP player, @Nullable Potion potion) {
        return potion != null && player.isPotionActive(potion);
    }

    /**
     * 主线程上处理一个血量包
     *
     * @param manager 这条连接（取它当前的包处理器，和原版 {@code NetworkManager} 处理包时用的是同一个）
     * @param packet  血量包
     */
    private static void handle(@Nonnull NetworkManager manager, @Nonnull SPacketUpdateHealth packet) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        if (player != null && packet.getHealth() < player.getHealth() && hasContinuousDrainEffect(player)) {
            // 只同步血量本身，跳过 hurtTime / hurtResistantTime 的赋值和那次 damageEntity，
            // 镜头不晃、本地无敌帧不被顶掉；饥饿值和饱和度照原版 handleUpdateHealth 更新
            player.setHealth(packet.getHealth());
            player.getFoodStats().setFoodLevel(packet.getFoodLevel());
            player.getFoodStats().setFoodSaturationLevel(packet.getSaturationLevel());
            return;
        }
        INetHandler handler = manager.getNetHandler();
        if (handler instanceof INetHandlerPlayClient) {
            ((INetHandlerPlayClient) handler).handleUpdateHealth(packet);
        }
    }

    /**
     * 管道里的血量包处理器（每条连接一个实例，不共享）
     */
    private static final class HealthHandler extends ChannelInboundHandlerAdapter {

        @Nonnull
        private final NetworkManager manager;

        HealthHandler(@Nonnull NetworkManager manager) {
            this.manager = manager;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (msg instanceof SPacketUpdateHealth) {
                final SPacketUpdateHealth packet = (SPacketUpdateHealth) msg;
                Minecraft.getMinecraft().addScheduledTask(() -> handle(manager, packet));
                return;
            }
            super.channelRead(ctx, msg);
        }
    }
}
