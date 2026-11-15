package pers.roinflam.carianstyle.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.carianstyle.client.gui.CodexScreen;
import pers.roinflam.carianstyle.platform.ClientPlatform;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.event.client.ClientTickEvent;

/**
 * 客户端按键绑定：打开附魔百科 / 特效开关界面。
 *
 * <h3>登记和响应分开</h3>
 * <p>
 * 按键由客户端入口经 {@link ClientContent} 调 {@link #register} 交给加载器登记
 * （Forge 上带「只在游戏内生效」的冲突上下文，到按键注册事件里才真正登记）；
 * 按下后的响应在 {@link Handler} 里，每个客户端 tick 末尾查一次。
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
public final class CarianStyleKeys {

    /** 按键分类名（显示在原版按键设置里的分组标题） */
    private static final String CATEGORY = "key.categories." + Reference.MOD_ID;

    /** 打开百科界面的按键名 */
    private static final String OPEN_CODEX_NAME = "key." + Reference.MOD_ID + ".open_codex";

    /** 打开百科界面的按键；登记之前是 null */
    private static KeyMapping openCodex;

    private CarianStyleKeys() {
    }

    /**
     * 把本模组的按键交给加载器登记。只许调一次。
     *
     * @param platform 客户端平台实现
     */
    public static void register(@Nonnull ClientPlatform platform) {
        if (openCodex != null) {
            throw new IllegalStateException("按键已经登记过了");
        }
        openCodex = platform.registerKeyMapping(OPEN_CODEX_NAME, GLFW.GLFW_KEY_K, CATEGORY);
    }

    /**
     * @return 打开百科界面的按键；还没登记时是 null
     */
    public static KeyMapping openCodex() {
        return openCodex;
    }

    /**
     * 运行时按键处理（客户端 tick）。
     */
    @pers.roinflam.carianstyle.bus.EventBusSubscriber(side = pers.roinflam.carianstyle.bus.EventBusSubscriber.Side.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        /**
         * 客户端 tick：消费按键点击并打开界面。
         * <p>
         * 只在 tick 末尾（{@code ClientTickEvent.Post}）且当前没有其它界面打开时响应。
         * {@code consumeClick()} 会把积压的点击一次性取走，
         * 因此不会出现「按一下开两次」的问题。
         * </p>
         *
         * @param event 客户端 tick 事件
         */
        @pers.roinflam.carianstyle.bus.SubscribeEvent
        public static void onClientTick(@Nonnull ClientTickEvent.Post event) {
            KeyMapping key = openCodex;
            if (key == null) {
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            // 已有界面打开时不响应，避免在聊天框里按 K 就弹出百科
            if (minecraft.screen != null || minecraft.player == null) {
                // 仍需清空积压的点击，否则关掉界面后会立刻再弹一次
                while (key.consumeClick()) {
                    // 丢弃
                }
                return;
            }

            boolean triggered = false;
            while (key.consumeClick()) {
                triggered = true;
            }

            if (triggered) {
                minecraft.setScreen(new CodexScreen());
            }
        }
    }
}
