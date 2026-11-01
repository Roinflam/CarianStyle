package pers.roinflam.carianstyle.mc112.gui;

import org.lwjgl.input.Keyboard;

import java.util.Arrays;

/**
 * 键码常量（数值和 GLFW 一致）+ LWJGL 2 键码的换算
 * <p>
 * 1.12.2 的键盘走 LWJGL 2，键码是 DirectInput 那一套（Esc = 1、A = 30）。界面的控件和页面按 GLFW 的数值写比较
 * （Esc = 256、A = 65、修饰键位掩码 Shift = 1 / Ctrl = 2 / Alt = 4），界面基类收到按键时先用 {@link #fromLwjgl} 换算，
 * 再交给控件，所以控件里的比较写法不用改，只是常量名少了 GLFW_ 前缀：{@code GLFW.GLFW_KEY_ESCAPE} 写成 {@link #KEY_ESCAPE}。
 * <p>
 * 原始的 LWJGL 键码照样作为 scanCode 传下去，要和 {@code KeyBinding#getKeyCode()} 比的地方用它。
 */
public final class Keys {

    public static final int KEY_UNKNOWN = -1;
    public static final int KEY_SPACE = 32;
    public static final int KEY_APOSTROPHE = 39;
    public static final int KEY_COMMA = 44;
    public static final int KEY_MINUS = 45;
    public static final int KEY_PERIOD = 46;
    public static final int KEY_SLASH = 47;
    public static final int KEY_0 = 48;
    public static final int KEY_1 = 49;
    public static final int KEY_2 = 50;
    public static final int KEY_3 = 51;
    public static final int KEY_4 = 52;
    public static final int KEY_5 = 53;
    public static final int KEY_6 = 54;
    public static final int KEY_7 = 55;
    public static final int KEY_8 = 56;
    public static final int KEY_9 = 57;
    public static final int KEY_SEMICOLON = 59;
    public static final int KEY_EQUAL = 61;
    public static final int KEY_A = 65;
    public static final int KEY_B = 66;
    public static final int KEY_C = 67;
    public static final int KEY_D = 68;
    public static final int KEY_E = 69;
    public static final int KEY_F = 70;
    public static final int KEY_G = 71;
    public static final int KEY_H = 72;
    public static final int KEY_I = 73;
    public static final int KEY_J = 74;
    public static final int KEY_K = 75;
    public static final int KEY_L = 76;
    public static final int KEY_M = 77;
    public static final int KEY_N = 78;
    public static final int KEY_O = 79;
    public static final int KEY_P = 80;
    public static final int KEY_Q = 81;
    public static final int KEY_R = 82;
    public static final int KEY_S = 83;
    public static final int KEY_T = 84;
    public static final int KEY_U = 85;
    public static final int KEY_V = 86;
    public static final int KEY_W = 87;
    public static final int KEY_X = 88;
    public static final int KEY_Y = 89;
    public static final int KEY_Z = 90;
    public static final int KEY_LEFT_BRACKET = 91;
    public static final int KEY_BACKSLASH = 92;
    public static final int KEY_RIGHT_BRACKET = 93;
    public static final int KEY_GRAVE_ACCENT = 96;
    public static final int KEY_ESCAPE = 256;
    public static final int KEY_ENTER = 257;
    public static final int KEY_TAB = 258;
    public static final int KEY_BACKSPACE = 259;
    public static final int KEY_INSERT = 260;
    public static final int KEY_DELETE = 261;
    public static final int KEY_RIGHT = 262;
    public static final int KEY_LEFT = 263;
    public static final int KEY_DOWN = 264;
    public static final int KEY_UP = 265;
    public static final int KEY_PAGE_UP = 266;
    public static final int KEY_PAGE_DOWN = 267;
    public static final int KEY_HOME = 268;
    public static final int KEY_END = 269;
    public static final int KEY_CAPS_LOCK = 280;
    public static final int KEY_SCROLL_LOCK = 281;
    public static final int KEY_NUM_LOCK = 282;
    public static final int KEY_PRINT_SCREEN = 283;
    public static final int KEY_PAUSE = 284;
    public static final int KEY_F1 = 290;
    public static final int KEY_F2 = 291;
    public static final int KEY_F3 = 292;
    public static final int KEY_F4 = 293;
    public static final int KEY_F5 = 294;
    public static final int KEY_F6 = 295;
    public static final int KEY_F7 = 296;
    public static final int KEY_F8 = 297;
    public static final int KEY_F9 = 298;
    public static final int KEY_F10 = 299;
    public static final int KEY_F11 = 300;
    public static final int KEY_F12 = 301;
    public static final int KEY_F13 = 302;
    public static final int KEY_F14 = 303;
    public static final int KEY_F15 = 304;
    public static final int KEY_F16 = 305;
    public static final int KEY_F17 = 306;
    public static final int KEY_F18 = 307;
    public static final int KEY_F19 = 308;
    public static final int KEY_KP_0 = 320;
    public static final int KEY_KP_1 = 321;
    public static final int KEY_KP_2 = 322;
    public static final int KEY_KP_3 = 323;
    public static final int KEY_KP_4 = 324;
    public static final int KEY_KP_5 = 325;
    public static final int KEY_KP_6 = 326;
    public static final int KEY_KP_7 = 327;
    public static final int KEY_KP_8 = 328;
    public static final int KEY_KP_9 = 329;
    public static final int KEY_KP_DECIMAL = 330;
    public static final int KEY_KP_DIVIDE = 331;
    public static final int KEY_KP_MULTIPLY = 332;
    public static final int KEY_KP_SUBTRACT = 333;
    public static final int KEY_KP_ADD = 334;
    public static final int KEY_KP_ENTER = 335;
    public static final int KEY_KP_EQUAL = 336;
    public static final int KEY_LEFT_SHIFT = 340;
    public static final int KEY_LEFT_CONTROL = 341;
    public static final int KEY_LEFT_ALT = 342;
    public static final int KEY_LEFT_SUPER = 343;
    public static final int KEY_RIGHT_SHIFT = 344;
    public static final int KEY_RIGHT_CONTROL = 345;
    public static final int KEY_RIGHT_ALT = 346;
    public static final int KEY_RIGHT_SUPER = 347;
    public static final int KEY_MENU = 348;

    /**
     * 修饰键位掩码（和 GLFW_MOD_* 一致）
     */
    public static final int MOD_SHIFT = 0x0001;
    public static final int MOD_CONTROL = 0x0002;
    public static final int MOD_ALT = 0x0004;
    public static final int MOD_SUPER = 0x0008;

    /**
     * LWJGL 2 键码 → GLFW 键码（下标是 LWJGL 键码）
     */
    private static final int[] FROM_LWJGL = new int[256];

    /**
     * GLFW 键码 → LWJGL 2 键码
     */
    private static final int[] TO_LWJGL = new int[KEY_MENU + 1];

    static {
        Arrays.fill(FROM_LWJGL, KEY_UNKNOWN);
        Arrays.fill(TO_LWJGL, Keyboard.KEY_NONE);
        map(Keyboard.KEY_ESCAPE, KEY_ESCAPE);
        // 主键盘数字行：LWJGL 是 1..9 在前、0 在最后
        map(Keyboard.KEY_1, KEY_1);
        map(Keyboard.KEY_2, KEY_2);
        map(Keyboard.KEY_3, KEY_3);
        map(Keyboard.KEY_4, KEY_4);
        map(Keyboard.KEY_5, KEY_5);
        map(Keyboard.KEY_6, KEY_6);
        map(Keyboard.KEY_7, KEY_7);
        map(Keyboard.KEY_8, KEY_8);
        map(Keyboard.KEY_9, KEY_9);
        map(Keyboard.KEY_0, KEY_0);
        map(Keyboard.KEY_MINUS, KEY_MINUS);
        map(Keyboard.KEY_EQUALS, KEY_EQUAL);
        map(Keyboard.KEY_BACK, KEY_BACKSPACE);
        map(Keyboard.KEY_TAB, KEY_TAB);
        map(Keyboard.KEY_Q, KEY_Q);
        map(Keyboard.KEY_W, KEY_W);
        map(Keyboard.KEY_E, KEY_E);
        map(Keyboard.KEY_R, KEY_R);
        map(Keyboard.KEY_T, KEY_T);
        map(Keyboard.KEY_Y, KEY_Y);
        map(Keyboard.KEY_U, KEY_U);
        map(Keyboard.KEY_I, KEY_I);
        map(Keyboard.KEY_O, KEY_O);
        map(Keyboard.KEY_P, KEY_P);
        map(Keyboard.KEY_LBRACKET, KEY_LEFT_BRACKET);
        map(Keyboard.KEY_RBRACKET, KEY_RIGHT_BRACKET);
        map(Keyboard.KEY_RETURN, KEY_ENTER);
        map(Keyboard.KEY_LCONTROL, KEY_LEFT_CONTROL);
        map(Keyboard.KEY_A, KEY_A);
        map(Keyboard.KEY_S, KEY_S);
        map(Keyboard.KEY_D, KEY_D);
        map(Keyboard.KEY_F, KEY_F);
        map(Keyboard.KEY_G, KEY_G);
        map(Keyboard.KEY_H, KEY_H);
        map(Keyboard.KEY_J, KEY_J);
        map(Keyboard.KEY_K, KEY_K);
        map(Keyboard.KEY_L, KEY_L);
        map(Keyboard.KEY_SEMICOLON, KEY_SEMICOLON);
        map(Keyboard.KEY_APOSTROPHE, KEY_APOSTROPHE);
        map(Keyboard.KEY_GRAVE, KEY_GRAVE_ACCENT);
        map(Keyboard.KEY_LSHIFT, KEY_LEFT_SHIFT);
        map(Keyboard.KEY_BACKSLASH, KEY_BACKSLASH);
        map(Keyboard.KEY_Z, KEY_Z);
        map(Keyboard.KEY_X, KEY_X);
        map(Keyboard.KEY_C, KEY_C);
        map(Keyboard.KEY_V, KEY_V);
        map(Keyboard.KEY_B, KEY_B);
        map(Keyboard.KEY_N, KEY_N);
        map(Keyboard.KEY_M, KEY_M);
        map(Keyboard.KEY_COMMA, KEY_COMMA);
        map(Keyboard.KEY_PERIOD, KEY_PERIOD);
        map(Keyboard.KEY_SLASH, KEY_SLASH);
        map(Keyboard.KEY_RSHIFT, KEY_RIGHT_SHIFT);
        map(Keyboard.KEY_MULTIPLY, KEY_KP_MULTIPLY);
        map(Keyboard.KEY_LMENU, KEY_LEFT_ALT);
        map(Keyboard.KEY_SPACE, KEY_SPACE);
        map(Keyboard.KEY_CAPITAL, KEY_CAPS_LOCK);
        map(Keyboard.KEY_F1, KEY_F1);
        map(Keyboard.KEY_F2, KEY_F2);
        map(Keyboard.KEY_F3, KEY_F3);
        map(Keyboard.KEY_F4, KEY_F4);
        map(Keyboard.KEY_F5, KEY_F5);
        map(Keyboard.KEY_F6, KEY_F6);
        map(Keyboard.KEY_F7, KEY_F7);
        map(Keyboard.KEY_F8, KEY_F8);
        map(Keyboard.KEY_F9, KEY_F9);
        map(Keyboard.KEY_F10, KEY_F10);
        map(Keyboard.KEY_NUMLOCK, KEY_NUM_LOCK);
        map(Keyboard.KEY_SCROLL, KEY_SCROLL_LOCK);
        map(Keyboard.KEY_NUMPAD7, KEY_KP_7);
        map(Keyboard.KEY_NUMPAD8, KEY_KP_8);
        map(Keyboard.KEY_NUMPAD9, KEY_KP_9);
        map(Keyboard.KEY_SUBTRACT, KEY_KP_SUBTRACT);
        map(Keyboard.KEY_NUMPAD4, KEY_KP_4);
        map(Keyboard.KEY_NUMPAD5, KEY_KP_5);
        map(Keyboard.KEY_NUMPAD6, KEY_KP_6);
        map(Keyboard.KEY_ADD, KEY_KP_ADD);
        map(Keyboard.KEY_NUMPAD1, KEY_KP_1);
        map(Keyboard.KEY_NUMPAD2, KEY_KP_2);
        map(Keyboard.KEY_NUMPAD3, KEY_KP_3);
        map(Keyboard.KEY_NUMPAD0, KEY_KP_0);
        map(Keyboard.KEY_DECIMAL, KEY_KP_DECIMAL);
        map(Keyboard.KEY_F11, KEY_F11);
        map(Keyboard.KEY_F12, KEY_F12);
        map(Keyboard.KEY_F13, KEY_F13);
        map(Keyboard.KEY_F14, KEY_F14);
        map(Keyboard.KEY_F15, KEY_F15);
        map(Keyboard.KEY_F16, KEY_F16);
        map(Keyboard.KEY_F17, KEY_F17);
        map(Keyboard.KEY_F18, KEY_F18);
        map(Keyboard.KEY_F19, KEY_F19);
        map(Keyboard.KEY_NUMPADEQUALS, KEY_KP_EQUAL);
        map(Keyboard.KEY_NUMPADENTER, KEY_KP_ENTER);
        map(Keyboard.KEY_RCONTROL, KEY_RIGHT_CONTROL);
        map(Keyboard.KEY_DIVIDE, KEY_KP_DIVIDE);
        map(Keyboard.KEY_SYSRQ, KEY_PRINT_SCREEN);
        map(Keyboard.KEY_RMENU, KEY_RIGHT_ALT);
        map(Keyboard.KEY_PAUSE, KEY_PAUSE);
        map(Keyboard.KEY_HOME, KEY_HOME);
        map(Keyboard.KEY_UP, KEY_UP);
        map(Keyboard.KEY_PRIOR, KEY_PAGE_UP);
        map(Keyboard.KEY_LEFT, KEY_LEFT);
        map(Keyboard.KEY_RIGHT, KEY_RIGHT);
        map(Keyboard.KEY_END, KEY_END);
        map(Keyboard.KEY_DOWN, KEY_DOWN);
        map(Keyboard.KEY_NEXT, KEY_PAGE_DOWN);
        map(Keyboard.KEY_INSERT, KEY_INSERT);
        map(Keyboard.KEY_DELETE, KEY_DELETE);
        map(Keyboard.KEY_LMETA, KEY_LEFT_SUPER);
        map(Keyboard.KEY_RMETA, KEY_RIGHT_SUPER);
        map(Keyboard.KEY_APPS, KEY_MENU);
    }

    private Keys() {
    }

    private static void map(int lwjgl, int glfw) {
        FROM_LWJGL[lwjgl] = glfw;
        TO_LWJGL[glfw] = lwjgl;
    }

    /**
     * LWJGL 2 键码换成 GLFW 键码
     *
     * @param lwjglKey {@code Keyboard.getEventKey()} 的值
     * @return GLFW 键码；没有对应的（输入法、日文键盘的特殊键）返回 {@link #KEY_UNKNOWN}
     */
    public static int fromLwjgl(int lwjglKey) {
        return lwjglKey > 0 && lwjglKey < FROM_LWJGL.length ? FROM_LWJGL[lwjglKey] : KEY_UNKNOWN;
    }

    /**
     * GLFW 键码换回 LWJGL 2 键码（和 KeyBinding 的键位比较时用）
     *
     * @param glfwKey GLFW 键码
     * @return LWJGL 键码；没有对应的返回 0（{@code Keyboard.KEY_NONE}）
     */
    public static int toLwjgl(int glfwKey) {
        return glfwKey >= 0 && glfwKey < TO_LWJGL.length ? TO_LWJGL[glfwKey] : Keyboard.KEY_NONE;
    }

    /**
     * 现在按着哪些修饰键（GLFW 位掩码）
     * <p>
     * Caps Lock / Num Lock 的锁定状态 LWJGL 2 读不到，这两位永远是 0。
     *
     * @return 位掩码
     */
    public static int currentModifiers() {
        int mods = 0;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            mods |= MOD_SHIFT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)) {
            mods |= MOD_CONTROL;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU)) {
            mods |= MOD_ALT;
        }
        if (Keyboard.isKeyDown(Keyboard.KEY_LMETA) || Keyboard.isKeyDown(Keyboard.KEY_RMETA)) {
            mods |= MOD_SUPER;
        }
        return mods;
    }
}
