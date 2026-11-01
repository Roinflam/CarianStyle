package pers.roinflam.carianstyle.mc112.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.SoundEvents;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 界面的零碎工具：时钟、剪贴板、点击音效、翻译、按码点移动光标、后台线程
 */
public final class Ui {

    private Ui() {
    }

    /**
     * 后台线程池（同 1.20.1 的 Util.backgroundExecutor：格式化、搜索、校验这类不该卡渲染的活）
     * <p>
     * 守护线程、低优先级，关游戏时不用等它们；线程数按核数取，最多 4 个。
     */
    @Nullable
    private static volatile ExecutorService background;

    /**
     * 后台线程池
     *
     * @return 线程池（第一次调用时创建）
     */
    @Nonnull
    public static ExecutorService backgroundExecutor() {
        ExecutorService executor = background;
        if (executor == null) {
            synchronized (Ui.class) {
                executor = background;
                if (executor == null) {
                    int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
                    AtomicInteger counter = new AtomicInteger();
                    executor = Executors.newFixedThreadPool(threads, task -> {
                        Thread thread = new Thread(task, "CarianStyle-Gui-Worker-" + counter.incrementAndGet());
                        thread.setDaemon(true);
                        thread.setPriority(Thread.MIN_PRIORITY);
                        return thread;
                    });
                    background = executor;
                }
            }
        }
        return executor;
    }

    @Nonnull
    public static Minecraft mc() {
        return Minecraft.getMinecraft();
    }

    /**
     * 毫秒时钟（动画、悬停计时用）
     *
     * @return 毫秒
     */
    public static long millis() {
        return Minecraft.getSystemTime();
    }

    /**
     * 读剪贴板文本
     *
     * @return 文本；读不到时为空串
     */
    @Nonnull
    public static String getClipboard() {
        try {
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                Object data = contents.getTransferData(DataFlavor.stringFlavor);
                return data == null ? "" : data.toString();
            }
        } catch (Exception ignored) {
            // 剪贴板被别的程序占着之类，当作空
        }
        return "";
    }

    /**
     * 写剪贴板
     * <p>
     * 原版 GuiScreen#setClipboardString 遇到空串直接不写，这里空串也写进去（复制一段空选区 = 清空剪贴板）。
     *
     * @param text 文本
     */
    public static void setClipboard(@Nullable String text) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text == null ? "" : text), null);
        } catch (Exception ignored) {
            // 写不进去就算了，不影响编辑
        }
    }

    /**
     * 按钮点击音
     *
     * @param handler 声音管理器（可为 null，null 时不响）
     */
    public static void playClick(@Nullable SoundHandler handler) {
        if (handler != null) {
            handler.playSound(PositionedSoundRecord.getMasterRecord(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * 按当前语言翻译（参数用 String.format 的规则代入）
     *
     * @param key  翻译键
     * @param args 参数
     * @return 译文；没有这个键时原样返回键名
     */
    @Nonnull
    public static String translate(@Nonnull String key, @Nonnull Object... args) {
        return I18n.format(key, args);
    }

    /**
     * 当前语言里有没有这个键
     *
     * @param key 翻译键
     * @return true = 有
     */
    public static boolean hasTranslation(@Nonnull String key) {
        return I18n.hasKey(key);
    }

    /**
     * 从 pos 开始按码点前后移动 delta 个字符（补充平面的字符算一个）
     *
     * @param text  文本
     * @param pos   起点
     * @param delta 正数往后、负数往前
     * @return 新位置
     */
    public static int offsetByCodepoints(@Nonnull String text, int pos, int delta) {
        int length = text.length();
        if (delta >= 0) {
            for (int i = 0; pos < length && i < delta; i++) {
                if (Character.isHighSurrogate(text.charAt(pos++)) && pos < length && Character.isLowSurrogate(text.charAt(pos))) {
                    pos++;
                }
            }
        } else {
            for (int i = delta; pos > 0 && i < 0; i++) {
                pos--;
                if (Character.isLowSurrogate(text.charAt(pos)) && pos > 0 && Character.isHighSurrogate(text.charAt(pos - 1))) {
                    pos--;
                }
            }
        }
        return pos;
    }
}
