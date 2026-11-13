package pers.roinflam.carianstyle.visual.client;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 叠层 HUD 覆盖层登记（客户端）。
 * <p>
 * 1.21.1 的 HUD 改成「层」（{@code LayeredDraw.Layer}），层的名字从字符串变成 {@link ResourceLocation}：
 * 原来的 {@code carianstyle_stack_hud} 对应 {@code carianstyle:stack_hud}。
 * 仍然放在所有层的最上面，和 1.20.1 的 {@code registerAboveAll} 一致。
 * 由客户端入口经 {@code ClientContent} 调 {@link #register}，加载器在自己的时机真正登记。
 * </p>
 * <p>
 * F1 隐藏界面时模组层照样会被调用，{@link StackHudOverlay} 自己判 {@code hideGui} 不画，表现不变。
 * </p>
 *
 * @author RoinFlam
 */
public final class StackHudRegister {

    /** 层的注册名 */
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "stack_hud");

    private StackHudRegister() {
    }

    /**
     * 登记覆盖层。
     *
     * @param platform 客户端平台实现
     */
    public static void register(@Nonnull ClientPlatform platform) {
        platform.registerHudAboveAll(LAYER_ID, StackHudOverlay.INSTANCE);
    }
}
