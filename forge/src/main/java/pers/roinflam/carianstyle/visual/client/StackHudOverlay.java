package pers.roinflam.carianstyle.visual.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import pers.roinflam.carianstyle.visual.StackDisplayRegistry;
import pers.roinflam.carianstyle.visual.StackHudManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 叠层 HUD 覆盖层（客户端）——常驻发光的玻璃卡片，满层时"燃烧"。
 * <p>
 * 屏幕左上角竖排显示所有叠层，每行一张卡片：左侧呼吸光条 + 名称 + “×层数” + 进度条。
 * 是否画进度条由<b>服务端下发的上限</b>决定（上限&gt;0 才画）。
 * <p>
 * <b>常驻动效（不满层也一直在动，让整体"活"起来）：</b>
 * <ul>
 *     <li>卡片外侧多层强调色辉光晕，随正弦缓慢呼吸；</li>
 *     <li>每张卡周期性掠过一道高光"扫光"（玻璃质感，按行错相，不会同时闪）；</li>
 *     <li>进度条为竖向玻璃渐变 + 持续流动的高光带 + 领头亮条，绝不瞬跳；</li>
 *     <li>顶部高光棱边 + 底部暗角，卡片有厚度感；圆角描边。</li>
 * </ul>
 * <p>
 * <b>满层"燃烧"特效（仅满层附近激活，平时一行 if 直接跳过、零开销）：</b>
 * 进度条转熔岩竖向渐变 + 横向扫动热点；条上沿窜起闪烁火舌；填充区升起淡出火星；
 * 描边/光条/辉光转为闪烁余烬橙；层数数字转热色并随心跳呼吸放大。火焰强度 {@code heat}
 * 平滑升降，进出满层不突兀。
 * <p>
 * <b>消失误闪修复：</b>仅"上一帧仍在显示列表中"的行在层数增长时才触发白闪；短暂消失后重现
 * （切走武器再切回、服务端计数器仍在增长）的那一帧只静默同步层数，不误闪。
 * <p>
 * <b>淡出末段闪烁修复（原版字体特性）：</b>Minecraft 的 {@code Font.drawInternal} 含
 * {@code if ((color & 0xFC000000) == 0) color |= 0xFF000000;}——当文字 alpha 字节 &lt; 4
 * （透明度极低）时强制改为完全不透明。卡片淡出末段 alpha 趋近 0 那一两帧，白色名称/层数文字
 * 会被原版强制全亮、闪一下。修复：文字 alpha 低于 {@link #MIN_TEXT_ALPHA} 直接跳过绘制
 * （此时本就近乎不可见），从根上绕开该分支；而 {@code fill}/{@code fillGradient} 不经过字体
 * 渲染、无此问题，故背景/光条/进度条填充仍平滑淡出。
 * <p>
 * 所有外观参数集中在顶部常量。全部基于帧间隔 dt，与帧率无关。
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
public final class StackHudOverlay implements IGuiOverlay {

    /** 单例 */
    public static final StackHudOverlay INSTANCE = new StackHudOverlay();

    /**
     * 名称翻译组件缓存：serialId -> 已构建的 {@link Component}。
     * <p><b>性能（视觉零变化）：</b>原先每帧每行都 {@code Component.translatable(...)} 新建一次组件
     * （每帧分配）。翻译组件在渲染时才解析当前语言，缓存其实例可跨帧复用、且语言切换仍正确，
     * 故按 serialId 缓存，仅每个附魔首帧分配一次。注册项数量极少（个位数），缓存有界、无需清理。
     * 仅客户端渲染线程访问，无并发问题。
     */
    private static final Map<Integer, Component> NAME_CACHE = new HashMap<>();

    // ===== 布局常量 =====
    private static final int ANCHOR_X = 6;
    private static final int ANCHOR_Y = 6;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_STRIDE = 25;
    private static final int BAR_WIDTH = 50;
    private static final int BAR_HEIGHT = 5;
    /** 出现/消失时的水平滑动距离（像素，负向为左） */
    private static final int SLIDE_PX = 18;

    /**
     * 文字最小可绘制透明度（4/255）。
     * <p>Minecraft 的 {@code Font.drawInternal} 在 {@code (color & 0xFC000000) == 0}
     * （即 alpha 字节 &lt; 4）时执行 {@code color |= 0xFF000000} 强制完全不透明，
     * 导致淡出末段文字突然全亮闪一下。低于此阈值直接跳过文字绘制（此时文字本就近乎不可见，
     * 跳过无视觉损失），从根源规避该原版特性引起的闪烁。
     * <p>取 4/255 是因为：只要传入 alpha &ge; 4/255，{@code Math.round(alpha*255)} 必 &ge; 4，
     * 高 6 位不全为 0，便不会触发上述强制不透明分支。
     */
    private static final float MIN_TEXT_ALPHA = 4f / 255f;

    // ===== 动画速度（指数平滑系数，越大越快）=====
    private static final float SPEED_ALPHA = 11f;
    private static final float SPEED_BAR = 9f;
    private static final float SPEED_Y = 13f;
    private static final float SPEED_X = 14f;
    private static final float SPEED_FLASH = 6f;

    // ===== 基础配色 =====
    private static final int COL_TEXT = 0xFFFFFF;
    private static final int COL_WHITE = 0xFFFFFF;
    private static final int COL_BG_TOP = 0x0B0E14;
    private static final int COL_BG_BOT = 0x05070A;
    private static final int COL_BLACK = 0x000000;

    // ===== 火焰配色（0xRRGGBB）=====
    /** 火芯：亮黄白 */
    private static final int COL_EMBER_HOT = 0xFFF0A0;
    /** 火中：橙 */
    private static final int COL_EMBER_MID = 0xFF9A2B;
    /** 火外：深橙红 */
    private static final int COL_EMBER_DEEP = 0xE0451A;

    // ===== 辉光 / 扫光 / 流光参数 =====
    /** 辉光基础不透明度 */
    private static final float GLOW_BASE = 0.05f;
    /** 辉光呼吸幅度 */
    private static final float GLOW_PULSE = 0.05f;
    /** 扫光周期（秒，每张卡掠过一次的间隔）*/
    private static final float GLINT_PERIOD = 5.5f;
    /** 扫光占周期比例（→ 实际扫光时长 ≈ 周期 × 该值）*/
    private static final float GLINT_SWEEP = 0.16f;
    /** 扫光带半宽（像素）*/
    private static final int GLINT_RADIUS = 5;
    /** 扫光峰值不透明度 */
    private static final float GLINT_ALPHA = 0.22f;
    /** 进度条流光速度 */
    private static final float BAR_FLOW_SPEED = 0.5f;
    /** 进度条流光半宽（像素）*/
    private static final int BAR_FLOW_RADIUS = 3;

    // ===== 火焰参数 =====
    /** 每行火星上限（固定，懒分配，仅满层时使用）*/
    private static final int EMBER_COUNT = 8;
    /** 满层时 heat 上升速度 */
    private static final float HEAT_RISE_SPEED = 4.5f;
    /** 离开满层时 heat 下降速度（稍慢，制造余烬感）*/
    private static final float HEAT_FALL_SPEED = 2.2f;
    /** heat=1 时每秒生成火星数 */
    private static final float EMBER_SPAWN_RATE = 13f;
    /** 火星上升速度基准（像素/秒）*/
    private static final float EMBER_RISE = 16f;
    /** 火星寿命（秒）*/
    private static final float EMBER_LIFE = 0.8f;
    /** 火舌数量 */
    private static final int FLAME_TONGUES = 5;

    /** 上一帧时间戳（毫秒），用于计算 dt */
    private long lastMs = 0L;

    /** 逐附魔动画状态：serialId -> 状态 */
    private final Map<Integer, Anim> anims = new HashMap<>();

    private StackHudOverlay() {
    }

    /**
     * 单个附魔的动画状态。
     */
    private static final class Anim {
        float barFill;   // 当前进度条填充比例（平滑趋近目标）
        float alpha;     // 当前不透明度（淡入淡出）
        float y;         // 当前 Y（平滑趋近目标行位）
        float exitX;     // 当前水平偏移（出现/消失滑动；0=就位，负=偏左）
        float flash;     // 闪烁强度（增层时置 1，衰减）
        float heat;      // 满层火焰强度 0~1（平滑升降）
        float targetRatio;
        float targetY;
        int lastCount;
        int lastMax;
        boolean present;          // 本帧是否仍在显示列表中
        boolean presentLastFrame; // 上一帧是否在显示列表中（抑制"消失再出现"误闪）
        boolean initialized;

        // —— 火星粒子（懒分配，仅满层时实例化；坐标相对进度条左上角）——
        float[] emberX;   // 相对填充区左端的 x（像素）
        float[] emberY;   // 高于进度条上沿的高度（像素，0=条顶，越大越高）
        float[] emberVy;  // 上升速度（像素/秒，正值）
        float[] emberLife;// 剩余寿命比例 1→0
        float[] emberSeed;// 每粒子相位种子（横向飘动差异）
        float emberSpawnAcc; // 生成累加器
        long rngState;       // 每行独立的无分配伪随机状态
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }

        List<StackHudManager.Entry> entries = StackHudManager.getEntries();

        // 计算帧间隔（秒），首帧为 0；夹取避免卡顿瞬间跳变
        long now = System.currentTimeMillis();
        float dt = (lastMs == 0L) ? 0f : Math.min(0.1f, (now - lastMs) / 1000f);
        lastMs = now;
        float time = now / 1000f;

        // 先把所有状态标记为“本帧未出现”
        for (Anim a : anims.values()) {
            a.present = false;
        }

        // 同步目标值（entries 已按 serialId 排序，索引即行序）
        int index = 0;
        for (StackHudManager.Entry entry : entries) {
            StackDisplayRegistry.Info info = StackDisplayRegistry.getInfo(entry.serialId());
            if (info == null) {
                continue;
            }
            Anim a = anims.get(entry.serialId());
            if (a == null) {
                a = new Anim();
                a.exitX = -SLIDE_PX; // 新行自左侧滑入
                anims.put(entry.serialId(), a);
            }

            int count = entry.count();
            int max = entry.max();
            float targetRatio = max > 0 ? Math.min(1f, (float) count / max) : 0f;
            float targetY = ANCHOR_Y + index * ROW_STRIDE;

            if (!a.initialized) {
                a.initialized = true;
                a.y = targetY;
                a.barFill = targetRatio;
                a.alpha = 0f;
                a.lastCount = count;
            }
            // 仅当上一帧就在显示列表、且确属真实增长时才触发白闪；
            // 刚（重新）出现的行这一帧只静默同步层数，避免误闪。
            if (count > a.lastCount && a.presentLastFrame) {
                a.flash = 1f;
            }
            a.lastCount = count;
            a.lastMax = max;
            a.targetRatio = targetRatio;
            a.targetY = targetY;
            a.present = true;
            index++;
        }

        // 更新动画并渲染；消失的行（滑出+淡出后）移除
        Font font = mc.font;
        Iterator<Map.Entry<Integer, Anim>> it = anims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Anim> e = it.next();
            int serialId = e.getKey();
            Anim a = e.getValue();

            float targetAlpha = a.present ? 1f : 0f;
            float targetExitX = a.present ? 0f : -SLIDE_PX;
            a.alpha = smooth(a.alpha, targetAlpha, SPEED_ALPHA, dt);
            a.exitX = smooth(a.exitX, targetExitX, SPEED_X, dt);
            a.barFill = smooth(a.barFill, a.targetRatio, SPEED_BAR, dt);
            a.y = smooth(a.y, a.targetY, SPEED_Y, dt);
            // 消失中的行（present=false，正在淡出）不应再出现白闪：直接清零残留闪光
            // （可能来自消失前最后一次层数增长或满层刷新），否则它会在淡出头几帧与卡片一起闪一下。
            a.flash = a.present ? smooth(a.flash, 0f, SPEED_FLASH, dt) : 0f;

            if (!a.present && a.alpha < 0.01f) {
                it.remove();
                continue;
            }

            StackDisplayRegistry.Info info = StackDisplayRegistry.getInfo(serialId);
            if (info != null) {
                renderRow(graphics, font, ANCHOR_X + Math.round(a.exitX), Math.round(a.y),
                        a, info, serialId, dt, time);
            }

            // 记录本帧是否出现，供下一帧"误闪抑制"判断
            a.presentLastFrame = a.present;
        }
    }

    /**
     * 渲染单张卡片。
     *
     * @param g        渲染上下文
     * @param font     字体
     * @param x        卡片左上角 X（已含水平滑动动画）
     * @param y        卡片左上角 Y（已含行位滑动动画）
     * @param a        动画状态
     * @param info     显示元数据
     * @param serialId 序列号（用于辉光/扫光/火焰相位去同步）
     * @param dt       帧间隔（秒，驱动火焰/火星）
     * @param time     全局时间（秒，驱动闪烁/扫动）
     */
    private void renderRow(GuiGraphics g, Font font, int x, int y, Anim a,
                           StackDisplayRegistry.Info info, int serialId, float dt, float time) {
        float alpha = a.alpha;
        int accent = info.color();
        int accentBright = brighten(accent, 0.35f);
        boolean hasBar = a.lastMax > 0;
        boolean atMax = hasBar && a.lastCount >= a.lastMax;

        // —— 火焰强度 heat 平滑（只有有进度条的行才会"燃烧"）——
        if (hasBar) {
            a.heat = smooth(a.heat, atMax ? 1f : 0f, atMax ? HEAT_RISE_SPEED : HEAT_FALL_SPEED, dt);
        }
        float heat = a.heat;

        // 名称翻译组件按 serialId 缓存，避免每帧每行重复 new（详见 NAME_CACHE）
        Component name = NAME_CACHE.get(serialId);
        if (name == null) {
            name = Component.translatable(info.nameKey());
            NAME_CACHE.put(serialId, name);
        }
        int nameWidth = font.width(name);
        String countText = "×" + a.lastCount;
        int countWidth = font.width(countText);

        int textX = x + 3 + 7; // 竖条(2~3) + 间距
        int line2Width = hasBar ? (countWidth + 6 + BAR_WIDTH) : countWidth;
        int contentRight = textX + Math.max(nameWidth, line2Width);
        int cardRight = contentRight + 7;
        int cardBottom = y + ROW_HEIGHT;

        // ===== 1. 外侧辉光晕（常驻呼吸；满层转余烬橙）=====
        float glowBreath = 0.5f + 0.5f * Mth.sin(time * 1.5f + serialId * 1.3f);
        int glowCol = heat > 0.02f ? lerpRgb(accent, COL_EMBER_MID, heat) : accent;
        float gA = (GLOW_BASE + GLOW_PULSE * glowBreath + 0.10f * heat) * alpha;
        drawHaloBorder(g, x, y, cardRight, cardBottom, 1, argb(glowCol, gA));
        drawHaloBorder(g, x, y, cardRight, cardBottom, 2, argb(glowCol, gA * 0.45f));

        // ===== 2. 卡片背景（竖向渐变；满层偏暖黑）=====
        int bgTop = heat > 0.02f ? lerpRgb(COL_BG_TOP, 0x1A0A04, heat * 0.7f) : lerpRgb(COL_BG_TOP, accent, 0.07f);
        g.fillGradient(x, y, cardRight, cardBottom, argb(bgTop, 0.52f * alpha), argb(COL_BG_BOT, 0.60f * alpha));

        // ===== 3. 底部暗角 + 顶部高光棱边（厚度感）=====
        g.fill(x + 1, cardBottom - 2, cardRight - 1, cardBottom - 1, argb(COL_BLACK, 0.28f * alpha));
        g.fill(x + 2, y + 1, cardRight - 2, y + 2, argb(brighten(accent, 0.55f), 0.16f * alpha));

        // ===== 4. 圆角描边（满层转热色闪烁）=====
        int borderColor;
        if (heat > 0.02f) {
            int hotBorder = lerpRgb(0xFF7A1E, COL_EMBER_HOT, flicker(time, serialId));
            borderColor = lerpRgb(accent, hotBorder, heat);
        } else {
            borderColor = accent;
        }
        drawRoundBorder(g, x, y, cardRight, cardBottom, argb(borderColor, (0.30f + 0.30f * heat) * alpha));

        // ===== 5. 玻璃扫光（周期掠过，按行错相）=====
        float glintCycle = frac((time + serialId * 0.61f) / GLINT_PERIOD);
        // 仅对仍在显示的行做扫光；淡出中的行不再扫光，避免消失瞬间又掠过一道高光。
        if (a.present && glintCycle < GLINT_SWEEP) {
            renderGlint(g, x, y + 1, cardRight, cardBottom - 1, glintCycle / GLINT_SWEEP, accent, alpha);
        }

        // ===== 6. 增层闪光覆盖（满层由白转橙，呈"爆燃"感）=====
        if (a.flash > 0.01f) {
            int flashCol = atMax ? lerpRgb(COL_WHITE, 0xFFB347, 0.55f) : COL_WHITE;
            g.fill(x, y, cardRight, cardBottom, argb(flashCol, 0.14f * a.flash * alpha));
        }

        // ===== 7. 左侧呼吸光条（竖向渐变；满层转余烬）=====
        float stripBreath = 0.75f + 0.25f * (0.5f + 0.5f * Mth.sin(time * 2f + serialId));
        int sTop = brighten(accent, 0.30f);
        int sBot = darken(accent, 0.15f);
        if (heat > 0.02f) {
            sTop = lerpRgb(sTop, COL_EMBER_HOT, heat * 0.85f);
            sBot = lerpRgb(sBot, COL_EMBER_DEEP, heat * 0.85f);
        }
        g.fillGradient(x + 1, y + 2, x + 3, cardBottom - 2,
                argb(sTop, stripBreath * (0.9f + 0.1f * a.flash) * alpha), argb(sBot, stripBreath * 0.8f * alpha));

        // ===== 8. 名称 =====
        // 规避 MC 字体「低 alpha 强制不透明」特性：alpha 低于阈值直接跳过（详见 MIN_TEXT_ALPHA），
        // 否则淡出末段（alpha 字节 1~3）文字会被原版强制全亮，造成消失瞬间闪一下。
        if (alpha >= MIN_TEXT_ALPHA) {
            g.drawString(font, name, textX, y + 3, argb(COL_TEXT, alpha), true);
        }

        // ===== 9. 层数（弹动 + 闪光 + 满层热色呼吸）=====
        int line2Y = y + 12;
        int numColor;
        if (atMax) {
            int hotNum = lerpRgb(0xFFD27A, COL_WHITE, 0.30f + 0.40f * flicker(time, serialId * 0.7f));
            numColor = lerpRgb(hotNum, COL_WHITE, a.flash);
        } else {
            numColor = lerpRgb(accentBright, COL_WHITE, a.flash);
        }
        float scale = 1f + 0.30f * a.flash + 0.06f * heat * (0.5f + 0.5f * Mth.sin(time * 6f));
        // 同样规避字体低 alpha 强制不透明特性（详见 MIN_TEXT_ALPHA）
        if (alpha >= MIN_TEXT_ALPHA) {
            drawScaledString(g, font, countText, textX, line2Y, argb(numColor, alpha), scale);
        }

        // ===== 10. 进度条 =====
        if (hasBar) {
            int barX = textX + countWidth + 6;
            int barY = line2Y + 1;
            int fillW = Math.round(BAR_WIDTH * a.barFill);

            // 轨道（内凹渐变 + 圆角描边）
            g.fillGradient(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                    argb(0x05070A, 0.9f * alpha), argb(0x10141A, 0.9f * alpha));
            drawBorder(g, barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT,
                    argb(lerpRgb(accent, COL_EMBER_DEEP, heat), 0.30f * alpha));

            if (fillW > 0) {
                if (heat > 0.02f) {
                    // 熔岩竖向渐变 + 横向扫动热点
                    float fl = flicker(time, serialId * 1.3f);
                    int hotTop = lerpRgb(0xFFC23A, COL_EMBER_HOT, 0.4f + 0.6f * fl);
                    int hotBot = lerpRgb(COL_EMBER_DEEP, COL_EMBER_MID, fl);
                    g.fillGradient(barX, barY, barX + fillW, barY + BAR_HEIGHT,
                            argb(hotTop, 0.95f * alpha), argb(hotBot, 0.95f * alpha));
                    float sweep = frac(time * 0.8f + serialId * 0.3f);
                    int hot = barX + Math.round(sweep * fillW);
                    g.fill(Math.max(barX, hot - 1), barY, Math.min(barX + fillW, hot + 2), barY + BAR_HEIGHT,
                            argb(COL_EMBER_HOT, 0.50f * fl * alpha));
                } else {
                    // 玻璃竖向渐变（上亮下深）+ 顶部高光 + 持续流光带
                    g.fillGradient(barX, barY, barX + fillW, barY + BAR_HEIGHT,
                            argb(accentBright, 0.95f * alpha), argb(accent, 0.95f * alpha));
                    g.fill(barX, barY, barX + fillW, barY + 1, argb(COL_WHITE, 0.28f * alpha));
                    renderBarFlow(g, barX, barY, fillW, time, serialId, alpha);
                }
                // 领头亮条（两种情况共用）
                if (fillW >= 2) {
                    g.fill(barX + fillW - 2, barY, barX + fillW, barY + BAR_HEIGHT, argb(COL_WHITE, 0.70f * alpha));
                }
            }

            // —— 满层火焰：火舌 + 火星（仅 heat 足够时绘制，平时零开销）——
            if (heat > 0.02f) {
                renderFlameTongues(g, barX, barY, fillW, alpha, time, heat);
                stepEmbers(a, dt, fillW);
                renderEmbers(g, a, barX, barY, alpha);
            }
        }
    }

    // ==================== 常驻动效子系统 ====================

    /**
     * 一道掠过卡片的玻璃高光"扫光"（带柔和抛物线衰减的竖向亮带）。
     *
     * @param p        扫光进度 0~1（从卡片左外侧扫到右外侧）
     * @param accent   强调色（与白混合作为高光色）
     * @param alpha    整卡不透明度
     */
    private static void renderGlint(GuiGraphics g, int x0, int y0, int x1, int y1,
                                    float p, int accent, float alpha) {
        int w = x1 - x0;
        float center = x0 - GLINT_RADIUS + p * (w + 2 * GLINT_RADIUS);
        int lo = Math.max(x0, Math.round(center - GLINT_RADIUS));
        int hi = Math.min(x1, Math.round(center + GLINT_RADIUS + 1));
        int glintCol = lerpRgb(accent, COL_WHITE, 0.7f);
        for (int cx = lo; cx < hi; cx++) {
            float d = (cx + 0.5f - center) / GLINT_RADIUS;
            float inten = 1f - d * d;
            if (inten <= 0f) {
                continue;
            }
            g.fill(cx, y0, cx + 1, y1, argb(glintCol, inten * GLINT_ALPHA * alpha));
        }
    }

    /**
     * 进度条填充区内持续流动的高光带（循环，制造液态流光）。
     */
    private static void renderBarFlow(GuiGraphics g, int barX, int barY, int fillW,
                                      float time, int serialId, float alpha) {
        if (fillW < 2) {
            return;
        }
        float flow = frac(time * BAR_FLOW_SPEED + serialId * 0.27f);
        float center = barX + flow * fillW;
        int lo = Math.max(barX, Math.round(center - BAR_FLOW_RADIUS));
        int hi = Math.min(barX + fillW, Math.round(center + BAR_FLOW_RADIUS + 1));
        for (int cx = lo; cx < hi; cx++) {
            float d = (cx + 0.5f - center) / BAR_FLOW_RADIUS;
            float inten = 1f - d * d;
            if (inten > 0f) {
                g.fill(cx, barY, cx + 1, barY + BAR_HEIGHT, argb(COL_WHITE, inten * 0.35f * alpha));
            }
        }
    }

    // ==================== 火焰子系统 ====================

    /**
     * 沿进度条上沿绘制若干窜动的火舌（竖向渐变，下实上透）。
     */
    private static void renderFlameTongues(GuiGraphics g, int barX, int barY, int fillW,
                                           float alpha, float time, float heat) {
        if (fillW < 2) {
            return;
        }
        for (int i = 0; i < FLAME_TONGUES; i++) {
            float fh = flicker(time + i * 0.6f, i * 2.1f);
            int th = Math.round((2f + 6f * fh) * heat);
            if (th <= 0) {
                continue;
            }
            int tx = barX + Math.round((i + 0.5f) / FLAME_TONGUES * fillW);
            int top = lerpRgb(COL_EMBER_MID, COL_EMBER_HOT, fh);
            g.fillGradient(tx, barY - th, tx + 1, barY, argb(top, 0f), argb(top, 0.55f * heat * alpha));
        }
    }

    /**
     * 推进火星粒子并按 heat 补充生成（无分配）。
     */
    private static void stepEmbers(Anim a, float dt, int fillW) {
        ensureEmberArrays(a);
        for (int i = 0; i < EMBER_COUNT; i++) {
            if (a.emberLife[i] > 0f) {
                a.emberLife[i] -= dt / EMBER_LIFE;
                a.emberY[i] += a.emberVy[i] * dt;
                a.emberX[i] += Mth.sin((a.emberY[i] + a.emberSeed[i]) * 0.5f) * 4f * dt;
            }
        }
        a.emberSpawnAcc += EMBER_SPAWN_RATE * a.heat * dt;
        int guard = 0;
        while (a.emberSpawnAcc >= 1f && guard++ < EMBER_COUNT) {
            a.emberSpawnAcc -= 1f;
            spawnEmber(a, fillW);
        }
        if (a.emberSpawnAcc > 2f) {
            a.emberSpawnAcc = 2f; // 防止长帧后一次性爆量
        }
    }

    /**
     * 在填充区内生成一颗火星（找空位，找不到则跳过）。
     */
    private static void spawnEmber(Anim a, int fillW) {
        int slot = -1;
        for (int i = 0; i < EMBER_COUNT; i++) {
            if (a.emberLife[i] <= 0f) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        int span = Math.max(2, fillW);
        a.emberX[slot] = rngFloat(a) * span;
        a.emberY[slot] = 0f;
        a.emberVy[slot] = EMBER_RISE * (0.65f + 0.7f * rngFloat(a));
        a.emberLife[slot] = 1f;
        a.emberSeed[slot] = rngFloat(a) * 6.2832f;
    }

    /**
     * 渲染所有存活火星：随寿命由亮黄→橙→暗红，寿命平方淡出，新生略大。
     */
    private static void renderEmbers(GuiGraphics g, Anim a, int barX, int barY, float alpha) {
        if (a.emberLife == null) {
            return;
        }
        for (int i = 0; i < EMBER_COUNT; i++) {
            float life = a.emberLife[i];
            if (life <= 0f) {
                continue;
            }
            int col;
            if (life > 0.66f) {
                col = lerpRgb(COL_EMBER_MID, COL_EMBER_HOT, (life - 0.66f) / 0.34f);
            } else if (life > 0.33f) {
                col = lerpRgb(COL_EMBER_DEEP, COL_EMBER_MID, (life - 0.33f) / 0.33f);
            } else {
                col = COL_EMBER_DEEP;
            }
            float ea = life * life * alpha * a.heat;
            int size = life > 0.6f ? 2 : 1;
            int xi = barX + Math.round(a.emberX[i]);
            int yi = barY - Math.round(a.emberY[i]);
            g.fill(xi, yi, xi + size, yi + size, argb(col, ea));
            if (size == 2) {
                g.fill(xi, yi, xi + 1, yi + 1, argb(COL_EMBER_HOT, ea * 0.9f));
            }
        }
    }

    /**
     * 懒分配火星数组（仅某行首次燃烧时触发一次）。
     */
    private static void ensureEmberArrays(Anim a) {
        if (a.emberLife == null) {
            a.emberX = new float[EMBER_COUNT];
            a.emberY = new float[EMBER_COUNT];
            a.emberVy = new float[EMBER_COUNT];
            a.emberLife = new float[EMBER_COUNT];
            a.emberSeed = new float[EMBER_COUNT];
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 帧率无关的指数平滑：value 向 target 趋近。
     */
    private static float smooth(float value, float target, float speed, float dt) {
        if (dt <= 0f) {
            return value;
        }
        float t = 1f - (float) Math.exp(-dt * speed);
        return value + (target - value) * t;
    }

    /**
     * 类火焰的廉价伪随机闪烁（多频正弦叠加，结果恒在 0~1）。
     */
    private static float flicker(float t, float seed) {
        float v = Mth.sin(t * 11f + seed) * 0.5f
                + Mth.sin(t * 19f + seed * 1.7f) * 0.3f
                + Mth.sin(t * 37f + seed * 2.3f) * 0.2f;
        return 0.5f + 0.5f * v;
    }

    /**
     * 取小数部分（结果恒在 0 到 1 之间，不含 1）。
     */
    private static float frac(float x) {
        return x - (float) Math.floor(x);
    }

    /**
     * 每行独立的无分配伪随机（xorshift64），返回 0~1。
     */
    private static float rngFloat(Anim a) {
        long s = a.rngState == 0L ? 0x9E3779B97F4A7C15L : a.rngState;
        s ^= s << 13;
        s ^= s >>> 7;
        s ^= s << 17;
        a.rngState = s;
        return ((s >>> 40) & 0xFFFFFFL) / (float) 0x1000000;
    }

    /**
     * 用缩放绘制字符串（围绕其中心缩放，用于层数弹动/呼吸）。
     */
    private static void drawScaledString(GuiGraphics g, Font font, String text, int x, int y, int color, float scale) {
        if (scale <= 1.001f) {
            g.drawString(font, text, x, y, color, true);
            return;
        }
        float w = font.width(text);
        float h = font.lineHeight;
        g.pose().pushPose();
        g.pose().translate(x + w / 2f, y + h / 2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, text, Math.round(-w / 2f), Math.round(-h / 2f), color, true);
        g.pose().popPose();
    }

    /**
     * 画 1px 直角边框（进度条等内部元素用）。
     */
    private static void drawBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y0 + 1, color);
        g.fill(x0, y1 - 1, x1, y1, color);
        g.fill(x0, y0, x0 + 1, y1, color);
        g.fill(x1 - 1, y0, x1, y1, color);
    }

    /**
     * 画 1px 圆角边框（四角各缺 1px，伪圆角，卡片外框用）。
     */
    private static void drawRoundBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, color);     // 上
        g.fill(x0 + 1, y1 - 1, x1 - 1, y1, color);     // 下
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);     // 左
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);     // 右
    }

    /**
     * 画一圈外发光晕（在卡片外侧 inset 像素处铺一圈淡色，四角留缺呈圆角辉光）。
     *
     * @param inset 向外偏移的像素
     */
    private static void drawHaloBorder(GuiGraphics g, int x0, int y0, int x1, int y1, int inset, int color) {
        int gx0 = x0 - inset, gy0 = y0 - inset, gx1 = x1 + inset, gy1 = y1 + inset;
        g.fill(gx0 + 1, gy0, gx1 - 1, gy0 + 1, color);   // 上
        g.fill(gx0 + 1, gy1 - 1, gx1 - 1, gy1, color);   // 下
        g.fill(gx0, gy0 + 1, gx0 + 1, gy1 - 1, color);   // 左
        g.fill(gx1 - 1, gy0 + 1, gx1, gy1 - 1, color);   // 右
    }

    /**
     * 把 0xRRGGBB + alpha(0~1) 打包为 ARGB。
     */
    private static int argb(int rgb, float alpha) {
        int a = Math.round(clamp01(alpha) * 255f);
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    /**
     * 在两个 0xRRGGBB 之间线性插值。
     */
    private static int lerpRgb(int from, int to, float t) {
        t = clamp01(t);
        int fr = (from >> 16) & 0xFF, fg = (from >> 8) & 0xFF, fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF, tg = (to >> 8) & 0xFF, tb = to & 0xFF;
        int r = Math.round(fr + (tr - fr) * t);
        int gg = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (r << 16) | (gg << 8) | b;
    }

    /** 向白混合（提亮）。 */
    private static int brighten(int rgb, float f) {
        return lerpRgb(rgb, COL_WHITE, f);
    }

    /** 向黑混合（压暗）。 */
    private static int darken(int rgb, float f) {
        return lerpRgb(rgb, COL_BLACK, f);
    }

    /** 夹取到 0~1。 */
    private static float clamp01(float v) {
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }
}
