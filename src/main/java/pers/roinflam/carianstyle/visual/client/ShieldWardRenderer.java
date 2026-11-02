package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import org.joml.Matrix4f;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * PORT-STUB(V3)
 *
 * 护盾类附魔的<b>物品局部空间</b>特效渲染器（纯客户端自绘）。
 * <p>
 * 负责魔力盾牌（魔力蓝护鞘）与不变盾牌（黄金树金护鞘）两个演出。
 * 由 {@code MixinItemInHandLayer}（第三人称）与
 * {@code MixinItemInHandRenderer}（第一人称）共同调用，两边视觉完全一致。
 * </p>
 *
 * <h2>v6.0：护鞘沿真实轮廓，不再是方盒</h2>
 * <p>
 * 几何全部由 {@link ShieldWardShape} 预先烘焙：真实轮廓、按距离场分级合并的面片、
 * 轮廓边段、闪点锚位。<b>每个模型只烘焙一次</b>，本类每帧只是遍历烘焙好的数组填顶点。
 * </p>
 * <p>
 * 之前是按包围盒画一个长方体壳。盾牌大多上圆下尖，方盒套上去底部两侧会露出大片空白护鞘。
 * 现在护鞘沿着盾的实际边缘走。
 * </p>
 *
 * <h3>余量用「绕中心缩放」而不是「按边外扩」</h3>
 * <p>
 * 护鞘要比盾大一圈（{@link #SHELL_MARGIN}），否则会与盾面 z-fighting。
 * 若把每条轮廓边段各自沿法线往外推，<b>凸角处相邻两段会裂开</b>，
 * 盾的每个折角都留一道缺口。改为对整个轮廓做绕中心的缩放，天然不裂。
 * </p>
 * <p>
 * 代价是余量在窄处略小于宽处（同一个缩放比例作用在不同半径上），
 * 但盾牌的长宽比不极端，肉眼看不出。
 * </p>
 *
 * <h3>配色</h3>
 * <ul>
 *     <li>只有魔力盾牌 —— 全程魔力蓝；</li>
 *     <li>只有不变盾牌 —— 全程黄金树金；</li>
 *     <li><b>两个都有</b> —— 在金与蓝之间来回插值，像呼吸灯一样缓慢切换。</li>
 * </ul>
 * <p>
 * 不变盾牌原来的「完全静止石壁」在 v5.0 换掉了，两个附魔的区分<b>只剩颜色</b>。
 * 金蓝色相差得够远，日常够用，但色弱或强环境光下辨识度不如从前。
 * </p>
 *
 * <h3>性能：这是常驻开销</h3>
 * <p>
 * 触发条件是「拿在手上」而非「举盾」，视野里每个持附魔盾的实体每帧都要画一份。
 * 沿轮廓画比方盒贵得多——轮廓边段有几十条，而描边、侧缘、背面描边、发光晕
 * 各要用它画一遍。控制手段：
 * </p>
 * <ul>
 *     <li>几何烘焙一次即缓存，每帧只填顶点，CPU 那头几乎不涨；</li>
 *     <li>{@link #SIMPLIFY_DISTANCE} 外<b>改用包围盒的四条边</b>，
 *         边段数从几十掉到 4，并去掉面片、辉光与闪点；</li>
 *     <li>{@link #CULL_DISTANCE} 外整个不画。</li>
 * </ul>
 * <p>
 * <b>嫌近距离太贵</b>：把 {@code ShieldWardShape.RES} 从 16 降到 12，
 * 轮廓边段数大约掉三分之一；或把 {@link #BACK_OUTLINE_RATIO} 设为 0，
 * 直接省掉一整遍描边。
 * </p>
 *
 * @author RoinFlam
 * @version 6.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class ShieldWardRenderer {
    private static final String SCHOLAR_SHIELD_ID = "scholar_shield";
    private static final String IMMUTABLE_SHIELD_ID = "immutable_shield";
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final float SIMPLIFY_DISTANCE = 12f;
    private static final double SIMPLIFY_DISTANCE_SQR = SIMPLIFY_DISTANCE * SIMPLIFY_DISTANCE;
    private static final float CULL_DISTANCE = 40f;
    private static final double CULL_DISTANCE_SQR = CULL_DISTANCE * CULL_DISTANCE;
    private static final float DETAIL_LOD_THRESHOLD = 0.75f;
    private static final float HALO_LOD_THRESHOLD = 0.55f;
    private static final float BACK_OUTLINE_LOD_THRESHOLD = 0.50f;
    private static final float SHELL_MARGIN = 0.038f;
    private static final float MARGIN_FLOOR_RATIO = 0.18f;
    private static final float APPEAR_SECONDS = 0.3f;
    private static final float MAX_STEP_SECONDS = 0.1f;
    private static final int MANA_CORE = 0xE4F2FF;
    private static final int MANA_MAIN = 0x4E9BFF;
    private static final int GOLD_CORE = 0xFFF3D2;
    private static final int GOLD_MAIN = 0xF0C246;
    private static final float SWITCH_PERIOD_SECONDS = 5f;
    private static final float SWITCH_OMEGA = TAU / SWITCH_PERIOD_SECONDS;
    private static final float FACE_ALPHA = 0.2f;
    private static final float RIM_ALPHA = 0.55f;
    private static final float RIM_BACK_RATIO = 0.35f;
    private static final float OUTLINE_ALPHA = 0.85f;
    private static final float BACK_OUTLINE_RATIO = 0.5f;
    private static final float OUTLINE_HALF = 0.016f;
    private static final float HALO_WIDTH = 0.07f;
    private static final float HALO_ALPHA = 0.3f;
    private static final int SPARK_COUNT = 3;
    private static final float SPARK_ARM = 0.075f;
    private static final float SPARK_HALF = 0.008f;
    private static final float SPARK_ALPHA = 0.95f;
    private static final float SPARK_PERIOD_SECONDS = 1.9f;
    private static final float SPARK_OMEGA = TAU / SPARK_PERIOD_SECONDS;
    private static final float GLINT_PERIOD = 0.62f;
    private static final float GLINT_SPEED = 0.42f;
    private static final float GLINT_SPEED_2 = -0.27f;
    private static final float GLINT_STRENGTH = 0.75f;
    private static final float GLINT_SECOND_WEIGHT = 0.6f;
    private static final float GLINT_CUTOFF = 0.03f;
    private static final float GLINT_STEP = 0.12f;
    private static final int GLINT_MAX_SPLIT = 8;
    private static final float BREATH_SPEED = 1.5f;
    private static final boolean FILM_BOTH_SIDES = false;
    private static final float LAYER_STEP = 0.0015f;
    private static final boolean DEBUG_ORIGIN_MARKER = false;
    private static final float[] DEBUG_MARKER_COLOR = {1f, 0.12f, 0.12f};
    private static final float DEBUG_MARKER_ARM = 0.16f;
    private static final float DEBUG_MARKER_HALF = 0.012f;
    private static final float[] C_MANA_CORE = VisualColor.constant(MANA_CORE);
    private static final float[] C_MANA_MAIN = VisualColor.constant(MANA_MAIN);
    private static final float[] C_GOLD_CORE = VisualColor.constant(GOLD_CORE);
    private static final float[] C_GOLD_MAIN = VisualColor.constant(GOLD_MAIN);
    private static final float[] MIX_CORE = new float[3];
    private static final float[] MIX_MAIN = new float[3];
    private static final Map<Integer, WardState> STATE = new HashMap<>();
    private static final long STATE_EXPIRY_MS = 3000L;

    private static final class WardState {
        float value;
        long lastUpdate;
    }
    private ShieldWardRenderer() {
    }
    public static void renderOnItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource buffer) {
    }
}

