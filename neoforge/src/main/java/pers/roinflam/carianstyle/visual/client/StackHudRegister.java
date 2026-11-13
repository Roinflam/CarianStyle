package pers.roinflam.carianstyle.visual.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import pers.roinflam.carianstyle.utils.Reference;

/**
 * 叠层 HUD 覆盖层注册（客户端，MOD 总线）。
 * <p>
 * 1.21.1 的 HUD 改成「层」（{@code LayeredDraw.Layer}），注册事件是 {@link RegisterGuiLayersEvent}，
 * 层的名字从字符串变成 {@link ResourceLocation}：原来的 {@code carianstyle_stack_hud} 对应
 * {@code carianstyle:stack_hud}。仍然放在所有层的最上面，和 1.20.1 的 {@code registerAboveAll} 一致。
 * </p>
 * <p>
 * F1 隐藏界面时模组层照样会被调用，{@link StackHudOverlay} 自己判 {@code hideGui} 不画，表现不变。
 * </p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Reference.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class StackHudRegister {

    /** 层的注册名 */
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "stack_hud");

    private StackHudRegister() {
    }

    /**
     * 注册覆盖层。
     *
     * @param event 注册事件
     */
    @SubscribeEvent
    public static void onRegisterLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, StackHudOverlay.INSTANCE);
    }
}
