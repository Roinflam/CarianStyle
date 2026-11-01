package pers.roinflam.carianstyle.mc112.gui;

import pers.roinflam.carianstyle.mc112.math.Mth;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.util.ChatAllowedCharacters;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * 单行输入框（逐行照 1.20.1 EditBox 移植，行为保持一致）
 * <p>
 * 和 1.12.2 GuiTextField 的区别：光标移动也会触发 responder（1.20.1 就是这样，界面靠它刷新状态）；
 * 格式化函数返回 {@link Component}，默认原样显示（§ 不解析）；可输入字符按 1.12.2 的
 * ChatAllowedCharacters 过滤。光标闪烁靠 {@link #tick()} 推进，不调就一直亮着。
 */
public class EditBox extends AbstractWidget {

    public static final int BACKWARDS = -1;
    public static final int FORWARDS = 1;
    public static final int DEFAULT_TEXT_COLOR = 0xE0E0E0;
    private static final int CURSOR_INSERT_COLOR = 0xFFD0D0D0;
    private static final int BORDER_COLOR_FOCUSED = 0xFFFFFFFF;
    private static final int BORDER_COLOR = 0xFFA0A0A0;
    private static final int BACKGROUND_COLOR = 0xFF000000;

    @Nonnull
    private final Font font;
    @Nonnull
    private String value = "";
    private int maxLength = 32;
    private int frame;
    private boolean bordered = true;
    private boolean canLoseFocus = true;
    private boolean isEditable = true;
    private boolean shiftPressed;
    private int displayPos;
    private int cursorPos;
    private int highlightPos;
    private int textColor = DEFAULT_TEXT_COLOR;
    private int textColorUneditable = 0x707070;
    @Nullable
    private String suggestion;
    @Nullable
    private Consumer<String> responder;
    @Nonnull
    private Predicate<String> filter = Objects::nonNull;
    @Nonnull
    private BiFunction<String, Integer, Component> formatter = (text, offset) -> Component.raw(text);
    @Nullable
    private Component hint;

    public EditBox(@Nonnull Font font, int x, int y, int width, int height, @Nonnull Component message) {
        this(font, x, y, width, height, null, message);
    }

    public EditBox(@Nonnull Font font, int x, int y, int width, int height, @Nullable EditBox copyFrom, @Nonnull Component message) {
        super(x, y, width, height, message);
        this.font = font;
        if (copyFrom != null) {
            setValue(copyFrom.getValue());
        }
    }

    public void setResponder(@Nullable Consumer<String> responder) {
        this.responder = responder;
    }

    /**
     * 显示用的格式化：参数是要画的那段文字和它在整串里的起点
     *
     * @param formatter 格式化函数
     */
    public void setFormatter(@Nonnull BiFunction<String, Integer, Component> formatter) {
        this.formatter = formatter;
    }

    public void tick() {
        ++frame;
    }

    public void setValue(@Nonnull String text) {
        if (filter.test(text)) {
            if (text.length() > maxLength) {
                value = text.substring(0, maxLength);
            } else {
                value = text;
            }
            moveCursorToEnd();
            setHighlightPos(cursorPos);
            onValueChange(text);
        }
    }

    @Nonnull
    public String getValue() {
        return value;
    }

    @Nonnull
    public String getHighlighted() {
        int start = Math.min(cursorPos, highlightPos);
        int end = Math.max(cursorPos, highlightPos);
        return value.substring(start, end);
    }

    public void setFilter(@Nonnull Predicate<String> filter) {
        this.filter = filter;
    }

    public void insertText(@Nonnull String text) {
        int start = Math.min(cursorPos, highlightPos);
        int end = Math.max(cursorPos, highlightPos);
        int room = maxLength - value.length() - (start - end);
        String filtered = ChatAllowedCharacters.filterAllowedCharacters(text);
        int length = filtered.length();
        if (room < length) {
            filtered = filtered.substring(0, room);
            length = room;
        }
        String result = new StringBuilder(value).replace(start, end, filtered).toString();
        if (filter.test(result)) {
            value = result;
            setCursorPosition(start + length);
            setHighlightPos(cursorPos);
            onValueChange(value);
        }
    }

    private void onValueChange(@Nonnull String text) {
        if (responder != null) {
            responder.accept(text);
        }
    }

    private void deleteText(int count) {
        if (Screen.hasControlDown()) {
            deleteWords(count);
        } else {
            deleteChars(count);
        }
    }

    public void deleteWords(int count) {
        if (!value.isEmpty()) {
            if (highlightPos != cursorPos) {
                insertText("");
            } else {
                deleteChars(getWordPosition(count) - cursorPos);
            }
        }
    }

    public void deleteChars(int count) {
        if (value.isEmpty()) {
            return;
        }
        if (highlightPos != cursorPos) {
            insertText("");
            return;
        }
        int target = getCursorPos(count);
        int start = Math.min(target, cursorPos);
        int end = Math.max(target, cursorPos);
        if (start != end) {
            String result = new StringBuilder(value).delete(start, end).toString();
            if (filter.test(result)) {
                value = result;
                moveCursorTo(start);
            }
        }
    }

    public int getWordPosition(int count) {
        return getWordPosition(count, getCursorPosition());
    }

    private int getWordPosition(int count, int from) {
        return getWordPosition(count, from, true);
    }

    private int getWordPosition(int count, int from, boolean skipSpaces) {
        int pos = from;
        boolean backwards = count < 0;
        int steps = Math.abs(count);
        for (int m = 0; m < steps; ++m) {
            if (!backwards) {
                int length = value.length();
                pos = value.indexOf(32, pos);
                if (pos == -1) {
                    pos = length;
                } else {
                    while (skipSpaces && pos < length && value.charAt(pos) == ' ') {
                        ++pos;
                    }
                }
            } else {
                while (skipSpaces && pos > 0 && value.charAt(pos - 1) == ' ') {
                    --pos;
                }
                while (pos > 0 && value.charAt(pos - 1) != ' ') {
                    --pos;
                }
            }
        }
        return pos;
    }

    public void moveCursor(int delta) {
        moveCursorTo(getCursorPos(delta));
    }

    private int getCursorPos(int delta) {
        return Ui.offsetByCodepoints(value, cursorPos, delta);
    }

    public void moveCursorTo(int pos) {
        setCursorPosition(pos);
        if (!shiftPressed) {
            setHighlightPos(cursorPos);
        }
        onValueChange(value);
    }

    public void setCursorPosition(int pos) {
        cursorPos = Mth.clamp(pos, 0, value.length());
    }

    public void moveCursorToStart() {
        moveCursorTo(0);
    }

    public void moveCursorToEnd() {
        moveCursorTo(value.length());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!canConsumeInput()) {
            return false;
        }
        shiftPressed = Screen.hasShiftDown();
        if (Screen.isSelectAll(keyCode)) {
            moveCursorToEnd();
            setHighlightPos(0);
            return true;
        } else if (Screen.isCopy(keyCode)) {
            Ui.setClipboard(getHighlighted());
            return true;
        } else if (Screen.isPaste(keyCode)) {
            if (isEditable) {
                insertText(Ui.getClipboard());
            }
            return true;
        } else if (Screen.isCut(keyCode)) {
            Ui.setClipboard(getHighlighted());
            if (isEditable) {
                insertText("");
            }
            return true;
        }
        switch (keyCode) {
            case Keys.KEY_BACKSPACE:
                if (isEditable) {
                    shiftPressed = false;
                    deleteText(-1);
                    shiftPressed = Screen.hasShiftDown();
                }
                return true;
            case Keys.KEY_DELETE:
                if (isEditable) {
                    shiftPressed = false;
                    deleteText(1);
                    shiftPressed = Screen.hasShiftDown();
                }
                return true;
            case Keys.KEY_RIGHT:
                if (Screen.hasControlDown()) {
                    moveCursorTo(getWordPosition(1));
                } else {
                    moveCursor(1);
                }
                return true;
            case Keys.KEY_LEFT:
                if (Screen.hasControlDown()) {
                    moveCursorTo(getWordPosition(-1));
                } else {
                    moveCursor(-1);
                }
                return true;
            case Keys.KEY_HOME:
                moveCursorToStart();
                return true;
            case Keys.KEY_END:
                moveCursorToEnd();
                return true;
            default:
                return false;
        }
    }

    public boolean canConsumeInput() {
        return isVisible() && isFocused() && isEditable();
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (!canConsumeInput()) {
            return false;
        }
        if (ChatAllowedCharacters.isAllowedCharacter(c)) {
            if (isEditable) {
                insertText(Character.toString(c));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        int offset = Mth.floor(mouseX) - getX();
        if (bordered) {
            offset -= 4;
        }
        String visible = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        moveCursorTo(font.plainSubstrByWidth(visible, offset).length() + displayPos);
    }

    @Override
    public void playDownSound(@Nonnull SoundHandler handler) {
    }

    @Override
    public void renderWidget(@Nonnull GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!isVisible()) {
            return;
        }
        if (isBordered()) {
            int border = isFocused() ? BORDER_COLOR_FOCUSED : BORDER_COLOR;
            g.fill(getX() - 1, getY() - 1, getX() + width + 1, getY() + height + 1, border);
            g.fill(getX(), getY(), getX() + width, getY() + height, BACKGROUND_COLOR);
        }
        int color = isEditable ? textColor : textColorUneditable;
        int cursor = cursorPos - displayPos;
        int highlight = highlightPos - displayPos;
        String visible = font.plainSubstrByWidth(value.substring(displayPos), getInnerWidth());
        boolean cursorVisible = cursor >= 0 && cursor <= visible.length();
        boolean blink = isFocused() && frame / 6 % 2 == 0 && cursorVisible;
        int textX = bordered ? getX() + 4 : getX();
        int textY = bordered ? getY() + (height - 8) / 2 : getY();
        int endX = textX;
        if (highlight > visible.length()) {
            highlight = visible.length();
        }
        if (!visible.isEmpty()) {
            String before = cursorVisible ? visible.substring(0, cursor) : visible;
            endX = g.drawString(font, formatter.apply(before, displayPos), textX, textY, color);
        }
        boolean insertCursor = cursorPos < value.length() || value.length() >= getMaxLength();
        int cursorX = endX;
        if (!cursorVisible) {
            cursorX = cursor > 0 ? textX + width : textX;
        } else if (insertCursor) {
            cursorX = endX - 1;
            --endX;
        }
        if (!visible.isEmpty() && cursorVisible && cursor < visible.length()) {
            g.drawString(font, formatter.apply(visible.substring(cursor), cursorPos), endX, textY, color);
        }
        if (hint != null && visible.isEmpty() && !isFocused()) {
            g.drawString(font, hint, endX, textY, color);
        }
        if (!insertCursor && suggestion != null) {
            g.drawString(font, suggestion, cursorX - 1, textY, 0xFF808080);
        }
        if (blink) {
            if (insertCursor) {
                g.fill(cursorX, textY - 1, cursorX + 1, textY + 1 + font.lineHeight, CURSOR_INSERT_COLOR);
            } else {
                g.drawString(font, "_", cursorX, textY, color);
            }
        }
        if (highlight != cursor) {
            int highlightX = textX + font.width(visible.substring(0, highlight));
            renderHighlight(g, cursorX, textY - 1, highlightX - 1, textY + 1 + font.lineHeight);
        }
    }

    private void renderHighlight(@Nonnull GuiGraphics g, int x1, int y1, int x2, int y2) {
        if (x1 < x2) {
            int t = x1;
            x1 = x2;
            x2 = t;
        }
        if (y1 < y2) {
            int t = y1;
            y1 = y2;
            y2 = t;
        }
        if (x2 > getX() + width) {
            x2 = getX() + width;
        }
        if (x1 > getX() + width) {
            x1 = getX() + width;
        }
        g.fillHighlight(x1, y1, x2, y2);
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
        if (value.length() > maxLength) {
            value = value.substring(0, maxLength);
            onValueChange(value);
        }
    }

    private int getMaxLength() {
        return maxLength;
    }

    public int getCursorPosition() {
        return cursorPos;
    }

    private boolean isBordered() {
        return bordered;
    }

    public void setBordered(boolean bordered) {
        this.bordered = bordered;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    public void setTextColorUneditable(int color) {
        this.textColorUneditable = color;
    }

    @Override
    public boolean canTabFocus() {
        return visible && isEditable && super.canTabFocus();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= getX() && mouseX < getX() + width && mouseY >= getY() && mouseY < getY() + height;
    }

    @Override
    public void setFocused(boolean focused) {
        if (canLoseFocus || focused) {
            super.setFocused(focused);
            if (focused) {
                frame = 0;
            }
        }
    }

    private boolean isEditable() {
        return isEditable;
    }

    public void setEditable(boolean editable) {
        this.isEditable = editable;
    }

    public int getInnerWidth() {
        return isBordered() ? width - 8 : width;
    }

    public void setHighlightPos(int pos) {
        int length = value.length();
        highlightPos = Mth.clamp(pos, 0, length);
        if (displayPos > length) {
            displayPos = length;
        }
        int inner = getInnerWidth();
        String visible = font.plainSubstrByWidth(value.substring(displayPos), inner);
        int visibleEnd = visible.length() + displayPos;
        if (highlightPos == displayPos) {
            displayPos -= font.plainSubstrByWidth(value, inner, true).length();
        }
        if (highlightPos > visibleEnd) {
            displayPos += highlightPos - visibleEnd;
        } else if (highlightPos <= displayPos) {
            displayPos -= displayPos - highlightPos;
        }
        displayPos = Mth.clamp(displayPos, 0, length);
    }

    public void setCanLoseFocus(boolean canLoseFocus) {
        this.canLoseFocus = canLoseFocus;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setSuggestion(@Nullable String suggestion) {
        this.suggestion = suggestion;
    }

    public int getScreenX(int index) {
        return index > value.length() ? getX() : getX() + font.width(value.substring(0, index));
    }

    public void setHint(@Nullable Component hint) {
        this.hint = hint;
    }

    /**
     * 字体（子类画字用）
     */
    @Nonnull
    protected Font font() {
        return font;
    }

    /**
     * 当前显示起点（第一个看得见的字符的下标）
     */
    public int getDisplayPos() {
        return displayPos;
    }

    /**
     * 选区另一端（没选中时等于光标位置）
     */
    public int getHighlightPos() {
        return highlightPos;
    }

    /**
     * 能不能改（setEditable 设的值）
     */
    public boolean isEditableBox() {
        return isEditable;
    }
}
