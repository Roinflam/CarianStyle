package pers.roinflam.carianstyle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;
import pers.roinflam.carianstyle.client.gui.CodexScreen;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 客户端按键绑定：打开附魔百科 / 特效开关界面。
 *
 * <h3>注册方式</h3>
 * <p>
 * 1.12.2 版：没有 MOD 总线上的按键注册事件，{@link #register()} 由 {@code ClientSetupHandler.init()} 调用，
 * 走 {@code ClientRegistry.registerKeyBinding}；运行时的按键处理照旧挂在 Forge 总线上（{@link Handler}）。
 * </p>
 *
 * <h3>默认按键</h3>
 * <p>
 * {@code K}。选它是因为原版与常见模组都没占用（{@code E} 背包、{@code B} 部分模组的仓库、
 * {@code R} 合成、{@code J/N} JEI 与 REI 系列、{@code M} 地图类模组）。
 * 冲突时玩家可在原版按键设置里改，本模组不做任何强制。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@SideOnly(Side.CLIENT)
public final class CarianStyleKeys {

    /** 按键分类名（显示在原版按键设置里的分组标题） */
    private static final String CATEGORY = "key.categories." + Reference.MOD_ID;

    /** 打开百科界面的按键 */
    public static final KeyBinding OPEN_CODEX = new KeyBinding(
            "key." + Reference.MOD_ID + ".open_codex",
            KeyConflictContext.IN_GAME,
            Keyboard.KEY_K,
            CATEGORY
    );

    private static boolean registered = false;

    private CarianStyleKeys() {
    }

    /**
     * 注册按键绑定（客户端 init，重复调用安全）。
     */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientRegistry.registerKeyBinding(OPEN_CODEX);
    }

    /**
     * 运行时按键处理（Forge 总线）。
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        /**
         * 客户端 tick：消费按键点击并打开界面。
         * <p>
         * 只在 {@code Phase.END} 且当前没有其它界面打开时响应。
         * {@code isPressed()} 每调一次取走一次积压的点击，循环取完，
         * 因此不会出现「按一下开两次」的问题。
         * </p>
         *
         * @param event 客户端 tick 事件
         */
        @SubscribeEvent
        public static void onClientTick(@Nonnull TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            // 已有界面打开时不响应，避免在聊天框里按 K 就弹出百科
            if (minecraft.currentScreen != null || minecraft.player == null) {
                // 仍需清空积压的点击，否则关掉界面后会立刻再弹一次
                while (OPEN_CODEX.isPressed()) {
                    // 丢弃
                }
                return;
            }

            boolean triggered = false;
            while (OPEN_CODEX.isPressed()) {
                triggered = true;
            }

            if (triggered) {
                minecraft.displayGuiScreen(new CodexScreen());
            }
        }
    }
}
