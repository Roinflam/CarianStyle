package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V3)
 *
 * 夏玻利利的灾祸「癫火之眼」客户端渲染器（纯客户端自绘，<b>零网络包</b>）。
 * <p>
 * 对应 {@code EnchantmentCalamity}：受到的伤害 ×1.5，且<b>怪物强烈倾向于攻击你</b>。
 * 这是一个诅咒——玩家不是主动装它的，而是被它缠上的。
 * </p>
 *
 * <h3>v2.0：把眼睛和癫火加回来</h3>
 * <p>
 * v1.0 只有牵引丝线，刻意避开了眼睛，理由是「嘶吼已经用掉眼睛这个符号」。
 * <b>这个取舍是错的。</b>夏玻利利的核心就是癫火与那只充血之眼——
 * 为了避让另一个附魔而把它的招牌拿掉，结果是八条红线谁也认不出是什么，
 * 既不像灾祸，也不像夏玻利利，只是「有个人身上挂了点红东西」。
 * </p>
 * <p>
 * v2.0 的做法是<b>把眼睛做成癫火本身</b>，靠癫火与嘶吼拉开距离：
 * </p>
 * <table border="1">
 *   <caption>与嘶吼的区分</caption>
 *   <tr><th></th><th>嘶吼（既有）</th><th>灾祸（本类）</th></tr>
 *   <tr><td>位置</td><td>敌人头顶</td><td><b>宿主自己的胸口</b></td></tr>
 *   <tr><td>眼型</td><td>充血之眼</td><td><b>竖瞳</b>，眼眶是焦黑的</td></tr>
 *   <tr><td>周围</td><td>无火</td><td><b>整个人在烧癫火</b>——眼上冒火、身周舔火舌</td></tr>
 *   <tr><td>其它</td><td>—</td><td>八条燃烧的癫火丝向外牵</td></tr>
 * </table>
 * <p>
 * <b>「整个人在烧」是最关键的一条。</b>嘶吼是敌人头上飘一只眼，
 * 灾祸是这个人从胸口到脚下全在冒黄火。哪怕两者同屏，
 * 也不会有人把「一只飘着的眼」和「一个烧起来的人」搞混。
 * </p>
 * <p>
 * <b>牵引丝线保留了</b>，但改成燃烧的癫火丝——它承担的是嘲讽机制
 * （怪物强烈倾向于攻击你）那半条语义，周期性「绷紧」表达被拽的力道。
 * 只是现在它不再是主角，而是从那只眼里烧出去的东西。
 * </p>
 *
 * <h3>配色</h3>
 * <p>
 * 主色是癫火黄 {@value #FRENZY_YELLOW}，配焦黑与一点充血红。
 * <b>癫火黄必须是主色</b>——之前用暗血红做主色是把「灾祸」理解成了流血，
 * 但它其实是癫火系的诅咒，黄色才对。血红只留在眼眶的血丝上，
 * 那是「充血之眼」的那点意思，不喧宾夺主。
 * </p>
 *
 * <h3>为什么不需要任何网络包</h3>
 * <p>
 * 生效条件只有一个：<b>身上装着带灾祸的护甲</b>。而附魔存在于 {@code ItemStack} 的 NBT 里、
 * 随装备槽位正常同步给全部观察者，客户端直接读得到。
 * 与 {@link DarkMoonRenderer} 判定暗月、{@link CarianRetaliationRenderer} 判定奉还是同一手法。
 * </p>
 * <p>
 * 因此本渲染器<b>不占用任何效果序列号、不新增任何包、服务端零开销</b>，
 * 也不需要改动 {@code EnchantmentCalamity} 一行代码。
 * </p>
 *
 * <h3>装备槽口径</h3>
 * <p>
 * {@link #hasCalamity} 目前只扫描<b>4 件护甲槽</b>——灾祸是诅咒类附魔，
 * 按本模组既有惯例（以及原版诅咒的做法）应当是护甲附魔。
 * </p>
 * <p>
 * <b>如果实际不是</b>，只需把该方法里的 {@code getArmorSlots()} 换成遍历
 * {@code EquipmentSlots.SCAN} 即可，其余代码一行不用动。
 * 这个判定错了的后果是「装上了却不显示」，不会崩溃、也不影响任何机制。
 * </p>
 *
 * <h3>顶点量与 LOD</h3>
 * <pre>
 * 癫火之眼：眼眶填充 72 + 双弧轮廓 144 + 竖瞳 24 + 血丝 24    264
 * 眼上火苗（3 簇 × 6 段 × 12）                               216
 * 癫火丝（8 条 × 3 段 × 十字双面 × 6）                       288
 * 丝端火簇（8 个 × 4 段 × 12）                               384
 * 身周火舌（6 条 × 6 段 × 12）                               432
 * 脚下焦痕 + 余烬（60 + 72）                                 132
 * ────────────────────────────────────────────────────────
 * 合计                                    ~1716 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 比 v1.0 的 444 重了近四倍——火焰是顶点大户，这是让它「够味道」的代价。
 * 因此本版对 LOD 的依赖比 v1.0 强得多，各层的保留阈值都调过：
 * </p>
 * <ul>
 *     <li><b>眼睛完全不削</b>（只缩弧线分段）——它是全部辨识度所在；</li>
 *     <li>身周火舌与丝端火簇是最贵的两层，也是最先被砍的；</li>
 *     <li>火焰的段数随细节缩放，远处的火苗退化成一小撮也仍是火。</li>
 * </ul>
 * <p>
 * 丝线与火舌都按<b>步长抽取</b>而非截断——它们的角度是均布的，
 * 截断前 N 条会让火只朝一侧烧。
 * </p>
 * <p>
 * 四个配色全是编译期常量、演出中只有 alpha 与尺寸在变、色相从不插值，
 * 故全部预解包为 {@code C_} 常量，颜色相关堆分配恒为 0，无需复用缓冲。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class CalamityRenderer {
    private static final String CALAMITY_ID = "calamity";
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int FRENZY_CORE = 0xFFF6C8;
    private static final int FRENZY_YELLOW = 0xFFD21E;
    private static final int FRENZY_CHAR = 0x2A1608;
    private static final int CALAMITY_BLOOD = 0x9E1A18;
    private static final float[] C_CORE = VisualColor.constant(FRENZY_CORE);
    private static final float[] C_YELLOW = VisualColor.constant(FRENZY_YELLOW);
    private static final float[] C_CHAR = VisualColor.constant(FRENZY_CHAR);
    private static final float[] C_BLOOD = VisualColor.constant(CALAMITY_BLOOD);
    private static final float EYE_HEIGHT_FACTOR = 0.68f;
    private static final float EYE_SIZE_FACTOR = 0.62f;
    private static final float EYE_HALF_WIDTH = 0.5f;
    private static final float EYE_HALF_HEIGHT = 0.26f;
    private static final int EYE_ARC_SEGMENTS = 12;
    private static final int EYE_ARC_SEGMENTS_MIN = 6;
    private static final float EYE_RIM_HALF = 0.018f;
    private static final float EYE_RIM_FLICKER = 0.035f;
    private static final float EYE_RIM_FLICKER_SPEED = 9.5f;
    private static final float EYE_ALPHA = 0.95f;
    private static final float PUPIL_HALF_WIDTH = 0.045f;
    private static final float PUPIL_HEIGHT_RATIO = 0.86f;
    private static final float PUPIL_PULSE_A = 1.7f;
    private static final float PUPIL_PULSE_B = 4.3f;
    private static final int VEIN_COUNT = 4;
    private static final float VEIN_HALF = 0.009f;
    private static final float VEIN_ALPHA = 0.7f;
    private static final float VEIN_KEEP_THRESHOLD = 0.55f;
    private static final int EYE_FLAME_COUNT = 3;
    private static final float EYE_FLAME_HEIGHT = 0.75f;
    private static final float EYE_FLAME_WIDTH = 0.2f;
    private static final float EYE_FLAME_KEEP_THRESHOLD = 0.35f;
    private static final int THREAD_COUNT = 8;
    private static final int THREAD_SEGMENTS = 3;
    private static final int THREAD_SEGMENTS_MIN = 2;
    private static final float THREAD_ORIGIN_FACTOR = 0.3f;
    private static final float THREAD_LENGTH_FACTOR = 1.85f;
    private static final float THREAD_SAG = 0.32f;
    private static final float THREAD_HALF_WIDTH = 0.024f;
    private static final float THREAD_ORBIT_SPEED = 0.14f;
    private static final float THREAD_WAVE_SPEED = 1.35f;
    private static final float THREAD_WAVE_AMOUNT = 0.16f;
    private static final float THREAD_BURN_SPEED = 11f;
    private static final float THREAD_BASE_ALPHA = 0.82f;
    private static final float THREAD_TIP_FLAME = 0.34f;
    private static final float THREAD_TIP_KEEP_THRESHOLD = 0.5f;
    private static final float TUG_PERIOD = 2.4f;
    private static final float TUG_WINDOW = 0.22f;
    private static final float TUG_SHORTEN = 0.28f;
    private static final int BODY_FLAME_COUNT = 6;
    private static final float BODY_FLAME_RADIUS = 0.5f;
    private static final float BODY_FLAME_HEIGHT = 0.62f;
    private static final float BODY_FLAME_WIDTH = 0.34f;
    private static final float BODY_FLAME_KEEP_THRESHOLD = 0.42f;
    private static final int FLAME_SEGMENTS = 6;
    private static final int FLAME_SEGMENTS_MIN = 3;
    private static final float FLAME_SWAY_SPEED = 5.2f;
    private static final float FLAME_SWAY_AMOUNT = 0.55f;
    private static final int CHAR_SEGMENTS = 20;
    private static final int CHAR_SEGMENTS_MIN = 8;
    private static final float CHAR_RADIUS_FACTOR = 1.15f;
    private static final float CHAR_ALPHA = 0.42f;
    private static final int EMBER_COUNT = 6;
    private static final float EMBER_SIZE = 0.04f;
    private static final float EMBER_KEEP_THRESHOLD = 0.5f;
    private CalamityRenderer() {
    }
}

