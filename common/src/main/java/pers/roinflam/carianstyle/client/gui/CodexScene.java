package pers.roinflam.carianstyle.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 书后面那幅湖夜画：night.png 按 1:1 界面像素贴，靠「月心那一列」和「湖岸线」对到卷首的月亮上；
 * 窗口比画大时，四边用画最外一圈像素拉伸补齐（天顶和湖底本来就是平的色，看不出接缝）。
 * 岸线上一层横移的薄雾，露在外面的十几颗亮星和几条湖面碎光明灭。
 */
public final class CodexScene {

    // night.png 的像素尺寸；scene.txt 里的岸线、月心列和星点坐标都是在这张图上量的，换图要一起改
    public static final int W = 1400;
    public static final int H = 1000;
    // 星点按 scene.txt 里的先后取，表里排在前面的先画
    private static final int MAX_STARS = 18;
    private static final int MAX_GLINTS = 8;
    private static final int MAX_EXTRA_STARS = 24;
    private static final int NIGHT = 0xFF02040D;

    private static final int DEFAULT_HORIZON = 440;
    private static final int DEFAULT_MOON_COLUMN = 620;

    private static boolean loaded;
    private static int horizon = DEFAULT_HORIZON;
    private static int moonColumn = DEFAULT_MOON_COLUMN;
    private static final List<int[]> STARS = new ArrayList<>();
    private static final List<int[]> GLINTS = new ArrayList<>();

    public interface Open {
        boolean test(int x, int y);
    }

    private CodexScene() {
    }

    public static int horizon() {
        load();
        return horizon;
    }

    public static int moonColumn() {
        load();
        return moonColumn;
    }

    /** 换过资源包后 scene.txt 可能变了；界面 init 时清掉重读，上次读坏了这次也能再试 */
    public static void reload() {
        loaded = false;
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        horizon = DEFAULT_HORIZON;
        moonColumn = DEFAULT_MOON_COLUMN;
        STARS.clear();
        GLINTS.clear();
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, "textures/gui/codex/scene.txt");
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(loc);
            if (res.isEmpty()) {
                return;
            }
            try (BufferedReader in = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    String[] p = line.trim().split(" ");
                    if (p.length < 2) {
                        continue;
                    }
                    switch (p[0]) {
                        case "hz" -> horizon = Integer.parseInt(p[1]);
                        case "mx" -> moonColumn = Integer.parseInt(p[1]);
                        case "s" -> STARS.add(new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2])});
                        case "g" -> GLINTS.add(new int[]{Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])});
                        default -> {
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 缺了星点表只是少几颗闪的星，画照样画
        }
    }

    public static void render(@Nonnull GuiGraphics g, int gw, int gh, int bgx, int hzy, float night, int starLimit,
                              @Nonnull Open open, @Nonnull Open uncovered, float alpha) {
        load();
        int bgy = hzy - horizon;
        String n = "night";
        int r = bgx + W;
        int b = bgy + H;
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        UiTheme.sprite(g, n, bgx, bgy, W, H, 0, 0, W, H, W, H);
        if (bgy > 0) {
            UiTheme.sprite(g, n, bgx, 0, W, bgy, 0, 0, W, 1, W, H);
        }
        if (b < gh) {
            UiTheme.sprite(g, n, bgx, b, W, gh - b, 0, H - 1, W, 1, W, H);
        }
        if (bgx > 0) {
            UiTheme.sprite(g, n, 0, bgy, bgx, H, 0, 0, 1, H, W, H);
            if (bgy > 0) {
                UiTheme.sprite(g, n, 0, 0, bgx, bgy, 0, 0, 1, 1, W, H);
            }
            if (b < gh) {
                UiTheme.sprite(g, n, 0, b, bgx, gh - b, 0, H - 1, 1, 1, W, H);
            }
        }
        if (r < gw) {
            UiTheme.sprite(g, n, r, bgy, gw - r, H, W - 1, 0, 1, H, W, H);
            if (bgy > 0) {
                UiTheme.sprite(g, n, r, 0, gw - r, bgy, W - 1, 0, 1, 1, W, H);
            }
            if (b < gh) {
                UiTheme.sprite(g, n, r, b, gw - r, gh - b, W - 1, H - 1, 1, 1, W, H);
            }
        }

        // 岸线上的薄雾倒过来贴，比卷首里那层淡
        RenderSystem.setShaderColor(1f, 1f, 1f, 0.55f * alpha);
        int mist = mistOffset();
        for (int x = mist - 128; x < gw; x += 128) {
            UiTheme.sprite(g, "mist", x, hzy, 128, 14, 0, 14, 128, -14, 128, 14);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (night > 0f) {
            g.fill(0, 0, gw, gh, UiTheme.withAlpha(NIGHT, night));
        }

        // 播完后露在外面的取前 18 颗；书挡住的那些只在书还没照出来时额外画
        long t = UiTheme.now();
        int shown = 0;
        int extra = 0;
        for (int i = 0; i < STARS.size() && i < starLimit; i++) {
            int x = STARS.get(i)[0] + bgx;
            int y = STARS.get(i)[1] + bgy;
            if (x < 1 || y < 1 || x >= gw - 1 || y >= gh - 1) {
                continue;
            }
            if (open.test(x, y)) {
                if (shown >= MAX_STARS) {
                    continue;
                }
                shown++;
            } else if (extra >= MAX_EXTRA_STARS || !uncovered.test(x, y)) {
                continue;
            } else {
                extra++;
            }
            float a = Math.max(twinkle(t, 2400 + (i % 5) * 500, (i % 7) * 600, 4), night * 0.7f) * alpha;
            if (i % 3 == 0) {
                UiTheme.spriteAlpha(g, "spark", x - 1, y - 1, 3, 3, a);
            } else if (a > 0f) {
                g.fill(x, y, x + 1, y + 1, UiTheme.withAlpha(0xFFFFFFFF, a));
            }
        }
        shown = 0;
        for (int i = 0; i < GLINTS.size() && shown < MAX_GLINTS; i++) {
            int[] p = GLINTS.get(i);
            int x = p[0] + bgx;
            int y = p[1] + bgy;
            if (x < 1 || y < 1 || x >= gw - 1 || y >= gh - 1 || !open.test(x, y) || !open.test(x + p[2], y)) {
                continue;
            }
            shown++;
            float a = twinkle(t, 2200, (i % 5) * 500, 3) * (1f - night) * alpha;
            if (a > 0f) {
                g.fill(x, y, x + p[2], y + 1, UiTheme.withAlpha(UiTheme.MOON, a));
            }
        }
    }

    /** 薄雾每 240 毫秒挪 1 像素，128 一循环；卷首那层也用它，两层对得上 */
    public static int mistOffset() {
        return (int) ((UiTheme.now() / 240L) % 128L);
    }

    private static float twinkle(long t, int period, int lead, int n) {
        long x = t + lead;
        long cycle = x / period;
        float f = (x % period) / (float) period;
        if ((cycle & 1L) == 1L) {
            f = 1f - f;
        }
        return (float) Math.floor(f * n) / n;
    }
}
