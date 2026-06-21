package pers.roinflam.carianstyle.visual.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 光环显示注册入口（纯客户端，需在客户端初始化阶段调用一次 {@link #init()}）。
 * <p>
 * 颜色、半径、形状、附魔 id 集中在此。范围判定已在 {@code EntityUtil.getNearbyEntities(类, 实体, R)}
 * 改为「精确小数坐标 + 水平圆（垂直 ±R 的圆柱）」，故形状统一为
 * {@link AuraDisplayRegistry.AuraShape#CIRCLE}，半径 R 即圆半径，与效果生效区逐像素一致。
 * <p>
 * 配色取自艾尔登法环原作意象：
 * <ul>
 *     <li>魔法之境（卡利亚辉石魔法）——青色；</li>
 *     <li>托普斯的立场——蓝色；</li>
 *     <li>回归性原理（塞乐恩的宇宙/法则魔法）——品红；</li>
 *     <li>圣域（黄金树信仰祷告）——金色。</li>
 * </ul>
 * <p>
 * <b>未接入说明：</b>
 * <ul>
 *     <li>重力 gravitas：计时重力场（命中后 ×4 秒），需读取服务端剩余冷却，客户端无此状态，暂不接入；</li>
 *     <li>艾奥尼亚 aeonia：经核对其为“给自身叠腐败/攻击回血”的自身效果，<b>没有范围 AOE</b>，故不作为光环。</li>
 * </ul>
 *
 * @author FlameForge
 */
@OnlyIn(Dist.CLIENT)
public final class CarianStyleAuraDisplays {

    // ===== 序列号（新增光环在此追加）=====
    public static final int TERRA_MAGICA = 1;
    public static final int TOPPS_STAND = 2;
    public static final int REGRESSIVE_PRINCIPLE = 3;
    public static final int HOLY_GROUND = 4;

    // ===== 配色（0xRRGGBB，艾尔登法环主题）=====
    /** 魔法之境：辉石魔法青 */
    private static final int COLOR_TERRA_MAGICA = 0x33D9C4;
    /** 托普斯的立场：蓝 */
    private static final int COLOR_TOPPS_STAND = 0x3D7BFF;
    /** 回归性原理：宇宙法则品红 */
    private static final int COLOR_REGRESSIVE = 0xE85CC0;
    /** 圣域：黄金树金 */
    private static final int COLOR_HOLY_GROUND = 0xFFC23A;

    /** 回归性原理半径上限（格），与源码 {@code Math.min(totalLevel*3, 8)} 一致 */
    private static final double REGRESSIVE_MAX_RADIUS = 8.0;

    private static boolean initialized = false;

    private CarianStyleAuraDisplays() {
    }

    /**
     * 注册全部光环显示项。重复调用安全。
     */
    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        // 魔法之境 realm_of_magic：6 格固定圆形（友方魔法增伤场，穿戴护甲即生效）
        AuraDisplayRegistry.register(TERRA_MAGICA, COLOR_TERRA_MAGICA, AuraDisplayRegistry.AuraShape.CIRCLE,
                AuraDisplayRegistry.fixed("realm_of_magic", 6.0));

        // 托普斯的立场 topps_stand：6 格固定圆形（魔法免疫场，穿戴护甲即生效）
        AuraDisplayRegistry.register(TOPPS_STAND, COLOR_TOPPS_STAND, AuraDisplayRegistry.AuraShape.CIRCLE,
                AuraDisplayRegistry.fixed("topps_stand", 6.0));

        // 回归性原理 regressive_principle：护甲等级之和 ×3 格、封顶 8 格的圆形（范围净化场）
        AuraDisplayRegistry.register(REGRESSIVE_PRINCIPLE, COLOR_REGRESSIVE, AuraDisplayRegistry.AuraShape.CIRCLE,
                AuraDisplayRegistry.scaledArmorSum("regressive_principle",
                        level -> Math.min(level * 3.0, REGRESSIVE_MAX_RADIUS)));

        // 圣域 holy_ground：举盾时 16 格圆形（友方减伤/增益场）
        AuraDisplayRegistry.register(HOLY_GROUND, COLOR_HOLY_GROUND, AuraDisplayRegistry.AuraShape.CIRCLE,
                AuraDisplayRegistry.blocking("holy_ground", 16.0));
    }
}
