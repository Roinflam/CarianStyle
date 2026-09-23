package pers.roinflam.carianstyle.dynamicattr.clientsync;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 客户端：隐藏处于卡利亚式隐身中的玩家模型（序列号 4）。
 *
 * <h3>为什么单独成类（2.2.0）</h3>
 * <p>
 * 此前这段逻辑写在 {@code DynamicAttributes.StealthEventHandler} 的实例方法里。
 * 那个处理器只在 {@code DynamicAttributeManager.apply} 中经
 * {@code DynamicAttributeInstance.setEventHandler} 注册到 {@code MinecraftForge.EVENT_BUS}，
 * 而 {@code apply} 的全部调用点都在服务端事件里（均有 {@code isClientSide} 守卫）。
 * </p>
 * <ul>
 *     <li><b>单人 / 局域网房主：</b>集成服务端和客户端在同一个 JVM、共用一条事件总线，
 *         服务端注册的处理器恰好能收到客户端的渲染事件——所以测试时一直是好的；</li>
 *     <li><b>独立服务器 / 局域网访客：</b>客户端进程从来不调用 {@code apply}，处理器从未注册，
 *         服务端下发的序列号 4 缓存在客户端<b>没有任何代码去查</b>，隐身者的模型对其他玩家始终可见。</li>
 * </ul>
 * <p>
 * 改为与 {@link ClientSyncFlameRenderer}（序列号 1~3）相同的形式：客户端静态订阅，
 * 每次渲染玩家时查一次序列号 4。查询是 O(1) 的 Set 查找，与原先每个处理器做的判断完全相同。
 * </p>
 *
 * <h3>行为</h3>
 * <p>
 * 取消 {@link RenderPlayerEvent.Pre} 即跳过整个 {@code PlayerRenderer.render}：模型、盔甲、手持物、
 * 名牌一并不画。碰撞箱与影子不在这条路径上（由 {@code EntityRenderDispatcher.render} 另行绘制），
 * 二者由 {@code MixinEntityRenderDispatcher} 处理。原版着火的火焰同样另行绘制，未做处理——
 * 与原版隐身药水一致（原版隐身的实体着火时火焰照样可见）。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Dist.CLIENT)
public final class ClientSyncStealthRenderer {

    /**
     * 隐身效果的同步序列号（见 {@code DynamicAttributes} 静态块里的 {@code ClientSyncAttribute.register}）。
     * <p>{@code MixinEntityRenderDispatcher} 隐藏影子时也用它。</p>
     */
    public static final int STEALTH_SERIAL = 4;

    private ClientSyncStealthRenderer() {
    }

    /**
     * 渲染玩家前：处于隐身同步列表中的玩家不渲染。
     *
     * @param event 玩家渲染前事件
     */
    @SubscribeEvent
    public static void onRenderPlayer(@Nonnull RenderPlayerEvent.Pre event) {
        if (ClientSyncEffectManager.shouldRenderEffect(STEALTH_SERIAL, event.getEntity().getId())) {
            event.setCanceled(true);
        }
    }
}
