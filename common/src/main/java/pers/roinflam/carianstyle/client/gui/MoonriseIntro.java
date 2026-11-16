package pers.roinflam.carianstyle.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import javax.annotation.Nonnull;

/**
 * 打开百科时的「月出」：夜色里一弯细月从湖岸线后升起、边升边盈，盈满那一下月晕外扩、夜色褪去，
 * 月光沿湖面由远到近铺下来，再顺着书缝把书照出来；最后大月逐档收拢，落回卷首的月盒
 * （开关页没有月盒：湖上露出满月时落到那一轮上，露不出就淡掉）。
 * 时间轴全按打开后的真实毫秒算，帧率高低只影响细腻程度，不影响快慢。
 */
final class MoonriseIntro {

    static final int RISE_AT = 180;
    static final int FULL_AT = 1050;
    static final int PATH_AT = 1080;
    static final int PATH_DUR = 380;
    static final int SEAM_AT = 1220;
    static final int SEAM_DUR = 150;
    static final int UNFOLD_AT = 1310;
    static final int UNFOLD_DUR = 380;
    static final int TABS_AT = 1400;
    static final int GATHER_AT = 1560;
    static final int GATHER_DUR = 240;
    static final int LAND_AT = GATHER_AT + GATHER_DUR;
    /** 光路、光尘、书页压暗都在这之前收完；指针走位跟卷首自己的时序，可能比这稍晚 */
    static final int END = 2100;

    /** 大月的几档尺寸，从大往小逐档换着收拢；80 和 48 就是卷首那两号 */
    private static final int[] SIZES = {128, 112, 96, 80, 64, 48};
    private static final int NIGHT = 0xFF02040D;
    private static final int DUST = 26;
    /** 盖在书和物品图标上面，物品图标画在 z 150 往上 */
    private static final float Z = 350f;

    private long start;
    private boolean landed;

    void begin(long t) {
        start = t;
        landed = false;
    }

    /** 直接跳到播完的样子 */
    void finish(long t) {
        start = t - END - 1000L;
        landed = true;
    }

    long start() {
        return start;
    }

    boolean running(long t) {
        return t - start < END;
    }

    /** 落位那一下只报一次，用来迸星点 */
    boolean takeLanding(long e) {
        if (landed || e < LAND_AT) {
            return false;
        }
        landed = true;
        return e < LAND_AT + 200;
    }

    // ---------------- 各段进度 ----------------

    private static float clamp(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float rise(long e) {
        return clamp((e - RISE_AT) / (float) (FULL_AT - RISE_AT));
    }

    /** 月相走得比位置慢：长时间停在细月，快到顶才盈满 */
    private static float wax(long e) {
        return (float) Math.pow(rise(e), 1.7);
    }

    /** 背景上压的夜色 */
    float night(long e) {
        if (e < FULL_AT) {
            return 0.86f - 0.08f * wax(e);
        }
        return 0.78f * (1f - UiTheme.easeOutCubic((e - FULL_AT) / 300f));
    }

    /** 夜里的星一颗颗亮起来 */
    int starLimit(long e) {
        return e >= FULL_AT ? Integer.MAX_VALUE : (int) Math.max(0L, (e - 40L) / 26L);
    }

    /** 书从书缝往两边被照出来的比例 */
    float reveal(long e) {
        return e <= UNFOLD_AT ? 0f : UiTheme.easeOutCubic((e - UNFOLD_AT) / (float) UNFOLD_DUR);
    }

    /** 刚照出来的书先暗后亮 */
    float bookShade(long e) {
        return 0.7f * (1f - clamp((e - UNFOLD_AT) / (float) (UNFOLD_DUR + 260)));
    }

    /** 能放下的最大一档：升到顶时上沿离屏幕顶至少 3 像素 */
    static int bigSize(int n, int hzy) {
        for (int s : SIZES) {
            if (s >= n && hzy - 5 - s >= 3) {
                return s;
            }
        }
        return n;
    }

    /** 月面半径，和贴图脚本里那几档一致 */
    private static int radius(int size) {
        return switch (size) {
            case 128 -> 43;
            case 112 -> 37;
            case 96 -> 32;
            case 80 -> 26;
            case 64 -> 20;
            default -> 14;
        };
    }

    private static String moonTex(int size) {
        return size == MoonDisc.LARGE ? "moonph_l" : size == MoonDisc.SMALL ? "moonph_s" : "moonph_" + size;
    }

    private static String glowTex(int size) {
        return size == MoonDisc.LARGE ? "glow_l" : size == MoonDisc.SMALL ? "glow_s" : "glow_" + size;
    }

    // ---------------- 画 ----------------

    /** 盈满那一下全屏闪一层月白 */
    void renderWash(@Nonnull GuiGraphics g, long e, int gw, int gh) {
        long d = e - FULL_AT;
        if (d < 0 || d >= 240) {
            return;
        }
        float k = 1f - d / 240f;
        g.fill(0, 0, gw, gh, UiTheme.withAlpha(0xFFEEF3FF, 0.16f * k * k));
    }

    /** 月光路：从岸线往近处一段段点亮，亮一下再落到四成，最后整条淡掉（画里本来那条光路留着） */
    void renderPath(@Nonnull GuiGraphics g, long e, int cx, int hzy, int bottom) {
        if (e < PATH_AT || e >= END) {
            return;
        }
        float fade = e < 1500 ? 1f : 1f - (e - 1500) / 450f;
        int depth = bottom - hzy;
        if (fade <= 0f || depth <= 4) {
            return;
        }
        g.drawManaged(() -> {
            int y = hzy + 3;
            int k = 0;
            while (y < bottom) {
                int d = y - hzy;
                long at = PATH_AT + Math.round(PATH_DUR * Math.pow(d / (double) depth, 1 / 1.6));
                if (e >= at) {
                    long age = e - at;
                    float a = (age < 90 ? 1f : Math.max(0.4f, 1f - (age - 90) / 420f)) * fade;
                    int color = age < 90 ? 0xFFFFFFFF : UiTheme.MOON;
                    int h = hash(k);
                    // 越近越宽、摆得越开，看着像透视
                    int spread = 3 + d / 5;
                    int w = Math.min(36, 2 + d / 7);
                    int x = cx - w / 2 + (h & 0xFF) * (spread * 2 + 1) / 256 - spread;
                    g.fill(x, y, x + w, y + 1, UiTheme.withAlpha(color, a));
                    if (((h >>> 8) & 3) == 0) {
                        int w2 = Math.max(1, w / 2);
                        int x2 = cx - w2 / 2 + ((h >>> 12) & 0xFF) * (spread * 2 + 1) / 256 - spread;
                        g.fill(x2, y, x2 + w2, y + 1, UiTheme.withAlpha(color, a * 0.7f));
                    }
                }
                y += 2 + d / 28;
                k++;
            }
        });
    }

    /** 一道银线顺着书缝从上划到下，书页展开时淡掉 */
    void renderSeam(@Nonnull GuiGraphics g, long e, int x, int top, int bottom) {
        if (e < SEAM_AT || e >= UNFOLD_AT + 260) {
            return;
        }
        float q = clamp((e - SEAM_AT) / (float) SEAM_DUR);
        int front = top + Math.round((bottom - top) * q * q);
        float a = e < UNFOLD_AT ? 1f : 1f - (e - UNFOLD_AT) / 260f;
        g.pose().pushPose();
        g.pose().translate(0f, 0f, Z);
        g.fill(x - 1, top, x, front, UiTheme.withAlpha(UiTheme.MOON, a * 0.45f));
        g.fill(x, top, x + 1, front, UiTheme.withAlpha(0xFFFFFFFF, a));
        g.fill(x + 1, top, x + 2, front, UiTheme.withAlpha(UiTheme.MOON, a * 0.45f));
        if (q < 1f) {
            g.fill(x - 1, Math.max(top, front - 6), x + 2, front, 0xFFFFFFFF);
        }
        g.pose().popPose();
    }

    /** 照亮的两道前沿：外沿一像素亮线，往里四像素退下去 */
    void renderEdges(@Nonnull GuiGraphics g, long e, int left, int right, int top, int bottom) {
        float p = reveal(e);
        if (p <= 0f || p >= 1f) {
            return;
        }
        float a = 1f - p;
        g.pose().pushPose();
        g.pose().translate(0f, 0f, Z);
        for (int i = 0; i < 5; i++) {
            int c = UiTheme.withAlpha(i == 0 ? 0xFFFFFFFF : UiTheme.MOON, a * (1f - i / 5f) * 0.9f);
            g.fill(left + i, top, left + i + 1, bottom, c);
            g.fill(right - 1 - i, top, right - i, bottom, c);
        }
        g.pose().popPose();
    }

    void renderShade(@Nonnull GuiGraphics g, long e, int x0, int y0, int x1, int y1) {
        float a = bookShade(e);
        if (a <= 0f || x1 <= x0) {
            return;
        }
        g.pose().pushPose();
        g.pose().translate(0f, 0f, Z);
        g.fill(x0, y0, x1, y1, UiTheme.withAlpha(NIGHT, a));
        g.pose().popPose();
    }

    /**
     * 大月：升起、盈满时的月晕外扩和光尘、收拢。收拢的锚点是月盒的下沿正中，
     * 所以大月和卷首月亮共用一条月心竖线，逐档换小就是「落进」月盒。
     *
     * @param cx          月心那一列（卷首月盒 / 湖上那轮的正中）
     * @param hzy         湖岸线，月盒下沿在它上面 5 像素
     * @param n           落位后的尺寸
     * @param big         升起时的尺寸，见 {@link #bigSize}
     * @param tint        月晕着色（选中附魔的分类色）
     * @param gatherFrame 收拢时的月相帧：卷首正从满月亏回去，跟着它走
     * @param fade        没有地方落，收拢时一起淡掉
     */
    void renderMoon(@Nonnull GuiGraphics g, long e, int cx, int hzy, int n, int big, int tint,
                    int gatherFrame, boolean fade) {
        if (e < RISE_AT || e >= LAND_AT) {
            return;
        }
        int size = big;
        int frame = MoonDisc.FRAMES - 1;
        float alpha = 1f;
        boolean clip = false;
        int y;
        if (e < FULL_AT) {
            float p = rise(e);
            float s = p * p * (3f - 2f * p);
            int y0 = hzy - big / 2 + radius(big);
            int y1 = hzy - 5 - big;
            y = Math.round(y0 + (y1 - y0) * s);
            frame = (int) Math.floor(wax(e) * (MoonDisc.FRAMES - 1));
            alpha = clamp((e - RISE_AT) / 260f);
            clip = true;
        } else if (e < GATHER_AT) {
            y = hzy - 5 - big;
        } else {
            float q = (e - GATHER_AT) / (float) GATHER_DUR;
            int steps = 0;
            for (int s : SIZES) {
                if (s > n && s <= big) {
                    steps++;
                }
            }
            if (steps == 0) {
                size = n;
            } else {
                int idx = Math.min(steps - 1, (int) (q * q * steps));
                for (int s : SIZES) {
                    if (s > n && s <= big && idx-- == 0) {
                        size = s;
                        break;
                    }
                }
            }
            frame = gatherFrame;
            y = hzy - 5 - size;
            if (fade) {
                alpha = 1f - q;
            }
        }
        int x = cx - size / 2;
        int cy = y + size / 2;

        g.pose().pushPose();
        g.pose().translate(0f, 0f, Z);
        if (e >= FULL_AT) {
            renderBloom(g, e, cx, hzy - 5 - big / 2, big, tint);
        }
        if (clip) {
            g.enableScissor(cx - big, 0, cx + big, hzy);
        }
        float glow = MoonDisc.glowCap(frame) * (e < FULL_AT ? 0.85f : 1f) * alpha;
        UiTheme.spriteTinted(g, glowTex(size), x, y, size, size, 0, 0, size, size, MoonDisc.glowTint(tint), glow);
        if (alpha > 0f) {
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            UiTheme.sprite(g, moonTex(size), x, y, size, size, frame * size, 0, size, size, size * MoonDisc.FRAMES, size);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        if (clip) {
            g.disableScissor();
        }
        if (e >= FULL_AT) {
            renderDust(g, e, cx, cy, radius(big), tint);
        }
        g.pose().popPose();
    }

    /** 盈满：月晕放大两倍、三倍涨开再退，两圈光环往外扩 */
    private void renderBloom(@Nonnull GuiGraphics g, long e, int cx, int cy, int big, int tint) {
        long d = e - FULL_AT;
        if (d >= 700) {
            return;
        }
        int glowRgb = MoonDisc.glowTint(tint);
        String tex = glowTex(big);
        for (int k = 3; k >= 2; k--) {
            float q = d / (k == 2 ? 480f : 620f);
            if (q >= 1f) {
                continue;
            }
            float a = (k == 2 ? 0.55f : 0.28f) * (1f - q);
            g.pose().pushPose();
            g.pose().translate(cx - big * k / 2f, cy - big * k / 2f, 0f);
            g.pose().scale(k, k, 1f);
            UiTheme.spriteTinted(g, tex, 0, 0, big, big, 0, 0, big, big, glowRgb, a);
            g.pose().popPose();
        }
        int r0 = radius(big) + 2;
        int ringRgb = UiTheme.mix(UiTheme.MOON, tint | 0xFF000000, 0.3f);
        ring(g, cx, cy, r0, r0 + Math.round(big * 1.3f), d, 420, 0.9f, ringRgb, false);
        ring(g, cx, cy, r0, r0 + big, d - 90, 460, 0.6f, ringRgb, true);
    }

    private static void ring(@Nonnull GuiGraphics g, int cx, int cy, int r0, int r1, long d, int dur,
                             float alpha, int rgb, boolean dotted) {
        if (d < 0 || d >= dur) {
            return;
        }
        float q = d / (float) dur;
        int r = r0 + Math.round((r1 - r0) * UiTheme.easeOutCubic(q));
        float a = alpha * (float) Math.pow(1f - q, 1.5);
        int c = UiTheme.withAlpha(rgb | 0xFF000000, a);
        g.drawManaged(() -> {
            int x = r;
            int y = 0;
            int err = 1 - r;
            while (x >= y) {
                if (!dotted || (y & 1) == 0) {
                    plot8(g, cx, cy, x, y, c);
                }
                y++;
                if (err < 0) {
                    err += 2 * y + 1;
                } else {
                    x--;
                    err += 2 * (y - x) + 1;
                }
            }
        });
    }

    private static void plot8(@Nonnull GuiGraphics g, int cx, int cy, int x, int y, int c) {
        g.fill(cx + x, cy + y, cx + x + 1, cy + y + 1, c);
        g.fill(cx - x - 1, cy + y, cx - x, cy + y + 1, c);
        g.fill(cx + x, cy - y - 1, cx + x + 1, cy - y, c);
        g.fill(cx - x - 1, cy - y - 1, cx - x, cy - y, c);
        if (x != y) {
            g.fill(cx + y, cy + x, cx + y + 1, cy + x + 1, c);
            g.fill(cx - y - 1, cy + x, cx - y, cy + x + 1, c);
            g.fill(cx + y, cy - x - 1, cx + y + 1, cy - x, c);
            g.fill(cx - y - 1, cy - x - 1, cx - y, cy - x, c);
        }
    }

    /** 光尘：从月缘迸出去，往上飘一点，四档淡出 */
    private void renderDust(@Nonnull GuiGraphics g, long e, int cx, int cy, int r, int tint) {
        long d = e - FULL_AT;
        if (d >= 1100) {
            return;
        }
        int color = UiTheme.mix(0xFFFFFFFF, tint | 0xFF000000, 0.25f);
        for (int i = 0; i < DUST; i++) {
            int h = hash(i + 17);
            float life = 650f + (h & 0xFF) / 255f * 420f;
            float q = d / life;
            if (q >= 1f) {
                continue;
            }
            double ang = i * 2.39996 + ((h >>> 8) & 0xF) * 0.03;
            float dist = r + 2 + (16 + ((h >>> 12) & 0x3F)) * UiTheme.easeOutCubic(q);
            int px = cx + (int) Math.round(Math.cos(ang) * dist);
            int py = cy + (int) Math.round(Math.sin(ang) * dist - q * 8);
            float a = q < 0.55f ? 1f : (float) Math.floor((1f - q) / 0.45f * 4f) / 4f;
            if (i % 5 == 0) {
                UiTheme.spriteAlpha(g, "spark", px - 1, py - 1, 3, 3, a);
            } else if (a > 0f) {
                g.fill(px, py, px + 1, py + 1, UiTheme.withAlpha(color, a));
            }
        }
    }

    private static int hash(int k) {
        k *= 0x9E3779B1;
        k ^= k >>> 15;
        k *= 0x85EBCA77;
        k ^= k >>> 13;
        return k;
    }
}
