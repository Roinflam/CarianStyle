package pers.roinflam.carianstyle.visual.client;

import net.minecraft.resources.ResourceLocation;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 叠层 HUD 覆盖层登记（客户端）。
 * <p>
 * 放在所有覆盖层的最上面。由客户端入口经 {@code ClientContent} 调 {@link #register}，加载器在自己的时机真正登记。
 * </p>
 *
 * @author RoinFlam
 */
public final class StackHudRegister {

    // Forge 上覆盖层名一直是 carianstyle:carianstyle_stack_hud，别的模组排层次时可能按这个名字认，别改
    private static final ResourceLocation LAYER_ID = new ResourceLocation(Reference.MOD_ID, "carianstyle_stack_hud");

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
