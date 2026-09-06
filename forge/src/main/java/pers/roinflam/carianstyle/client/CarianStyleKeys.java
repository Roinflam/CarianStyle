package pers.roinflam.carianstyle.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import pers.roinflam.carianstyle.client.gui.CodexScreen;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;

/**
 * 客户端按键绑定：打开附魔百科 / 特效开关界面。
 *
 * <h3>为什么用两个 EventBusSubscriber</h3>
 * <p>
 * {@link RegisterKeyMappingsEvent} 走 <b>MOD</b> 总线（模组生命周期事件），
 * {@link TickEvent.ClientTickEvent} 走 <b>FORGE</b> 总线（游戏运行时事件），
 * 二者不在同一条总线上，因此拆成外层类与内层类分别订阅。
 * </p>
 * <p>
 * 两个订阅都是注解式自注册，<b>不需要在模组主类里加任何代码</b>——
 * 这样本次新增的功能可以整包加入 / 整包删除，不会在 {@code CarianStyle.java}
 * 里留下需要一并回滚的接线。
 * </p>
 *
 * <h3>默认按键</h3>
 * <p>
 * {@code K}。选它是因为原版与常见模组都没占用（{@code E} 背包、{@code B} 部分模组的仓库、
 * {@code R} 合成、{@code J/N} JEI 与 REI 系列、{@code M} 地图类模组）。
 * 冲突时玩家可在原版按键设置里改，本模组不做任何强制。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CarianStyleKeys {

    /** 按键分类名（显示在原版按键设置里的分组标题） */
    private static final String CATEGORY = "key.categories." + Reference.MOD_ID;

    /** 打开百科界面的按键 */
    public static final KeyMapping OPEN_CODEX = new KeyMapping(
            "key." + Reference.MOD_ID + ".open_codex",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY
    );

    private CarianStyleKeys() {
    }

    /**
     * 注册按键绑定。
     *
     * @param event 按键注册事件
     */
    @SubscribeEvent
    public static void onRegisterKeyMappings(@Nonnull RegisterKeyMappingsEvent event) {
        event.register(OPEN_CODEX);
    }

    /**
     * 运行时按键处理（FORGE 总线）。
     */
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Handler {

        private Handler() {
        }

        /**
         * 客户端 tick：消费按键点击并打开界面。
         * <p>
         * 只在 {@code Phase.END} 且当前没有其它界面打开时响应。
         * {@code consumeClick()} 会把积压的点击一次性取走，
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

            Minecraft minecraft = Minecraft.getInstance();
            // 已有界面打开时不响应，避免在聊天框里按 K 就弹出百科
            if (minecraft.screen != null || minecraft.player == null) {
                // 仍需清空积压的点击，否则关掉界面后会立刻再弹一次
                while (OPEN_CODEX.consumeClick()) {
                    // 丢弃
                }
                return;
            }

            boolean triggered = false;
            while (OPEN_CODEX.consumeClick()) {
                triggered = true;
            }

            if (triggered) {
                minecraft.setScreen(new CodexScreen());
            }
        }
    }
}
