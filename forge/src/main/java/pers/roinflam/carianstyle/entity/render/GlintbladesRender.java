package pers.roinflam.carianstyle.entity.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;

import javax.annotation.Nonnull;

/**
 * 魔法辉剑实体渲染器（卡利亚风格：剑尖实时锁定目标）。
 *
 * <h3>解决的问题</h3>
 * <p>
 * 旧实现的姿态是这样的：悬浮期 {@code Axis.YP.rotationDegrees(tickCount * 20)} 绕 Y 轴匀速自转，
 * 飞行期用 {@code getXRot()} 做一次线性映射。两者都不对：
 * </p>
 * <ul>
 *     <li>悬浮期的自转让剑尖朝哪全看运气，完全没有「一圈剑锁着你」的压迫感；</li>
 *     <li>{@code getXRot()} 只有俯仰、<b>没有偏航</b>，飞行中的剑在水平面上永远朝同一个方向，
 *         斜着飞出去时看起来像在平移而不是突刺；</li>
 *     <li>旋转值随实体位置按 {@code updateInterval}（当前注册为 10 tick）同步，客户端拿到的是
 *         阶梯状离散值，剑尖会一跳一跳。</li>
 * </ul>
 * <p>
 * 现改为每帧从 {@link EntityGlintblades#getAimDirection(float)} 取方向自行摆姿势——
 * 该方法在客户端用目标实体的<b>插值位置</b>实时计算，与位置同步频率彻底解耦。
 * </p>
 *
 * <h3>斜向素材的轴向补偿（关键，改素材前先看这里）</h3>
 * <p>
 * {@code glintblades.png} 与绝大多数 MC 剑类贴图一样，刀身是<b>沿 45° 对角线</b>画的
 * （剑柄在左下、剑尖在右上）。而下面的姿态数学是按「刀身沿局部 +X 轴」推导的，
 * 因此必须先把素材掰正：在<b>最内层</b>补一个 {@code ZP(-45°)}，
 * 把对角线方向 {@code (1,1,0)} 转到 {@code (1,0,0)}。
 * </p>
 * <p>
 * <b>所以素材不需要重画</b>——只有 {@link #TEXTURE_TIP_ANGLE_DEG} 一个常量需要对：
 * </p>
 * <ul>
 *     <li>剑尖在贴图<b>右上</b>（最常见）→ {@code 45}（默认值）；</li>
 *     <li>剑尖在<b>左上</b> → {@code 135}；</li>
 *     <li>剑尖在<b>左下</b> → {@code -135}；</li>
 *     <li>剑尖在<b>右下</b> → {@code -45}；</li>
 *     <li>刀身是<b>竖直</b>的（剑尖朝上）→ {@code 90}。</li>
 * </ul>
 * <p>
 * 判断方法很简单：进游戏看一眼，如果剑是<b>屁股朝着目标</b>飞，就把该值 {@code +180}。
 * </p>
 *
 * <h3>姿态数学</h3>
 * <p>
 * 变换按 {@code R = YP(yaw) · ZP(pitch) · XP(roll) · ZP(-tipAngle)} 复合。
 * {@link PoseStack} 的语义是「后调用的先作用于模型」，故代码里的 {@code mulPose} 顺序
 * 与上式<b>从左到右一致</b>。推导（右手系）：
 * </p>
 * <pre>
 * ZP(-tipAngle) 把素材刀身轴 (1,1,0) 掰到 +X
 * XP(roll)      绕刀身轴自转（不改变剑尖朝向，只决定「刀面朝哪」）
 * ZP(pitch)     +X → (cos p, sin p, 0)
 * YP(yaw)       (x,y,z) → (x·cos y + z·sin y, y, -x·sin y + z·cos y)
 *
 * ⇒ 最终刀身轴 d = (cos p·cos y,  sin p,  -cos p·sin y)
 * ⇒ pitch = atan2(dy, √(dx²+dz²))
 * ⇒ yaw   = atan2(-dz, dx)
 * </pre>
 *
 * <h3>自转（v4.0：取代原先的 billboard roll）</h3>
 * <p>
 * 老头环里的辉剑是<b>绕自身刀身轴持续自转</b>的，故 roll 改为 {@code time × 自转速度}。
 * </p>
 * <p>
 * <b>代价与缓解：</b>贴图是一张挤出片，自转到侧面时会变薄。但它不会消失——
 * {@code item/generated} 会沿贴图 alpha 轮廓生成<b>侧面</b>，侧对时看到的是一条沿刀刃的亮边，
 * 配合满亮度自发光读起来像「刀刃反光一闪」。默认挤出厚度只有 1/16 格，在 size=1 的小剑上偏薄，
 * 故用 {@link #Z_THICKNESS} 在模型空间沿贴图法线加厚（默认 2.5 倍）。
 * </p>
 * <p>
 * <b>{@link #Z_THICKNESS} 必须是最后一个 {@code mulPose}/{@code scale} 调用</b>：
 * {@link PoseStack} 后调用的先作用于模型，只有排在最后它才是在<b>模型空间</b>沿贴图法线加厚；
 * 若提前，就变成在世界空间沿 Z 轴拉伸，剑会被压扁成畸形。
 * </p>
 * <p>
 * 自转速度随体积开方衰减——巨剑阵 size=7.5 若与小剑同速会转得像电风扇，
 * 慢下来才有重量感。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * 每把剑每帧：1 次 {@code sqrt} + 4 次 {@code atan2}/三角函数 + 2 次点积，
 * 与旧实现的一次 {@code rotationDegrees} 属同一量级，可忽略。
 * {@link ItemStack} 提为字段避免每帧 {@code new}——卡利亚圆阵单次触发就是 8 把剑，
 * 60fps 下旧写法每秒白白分配近 500 个 ItemStack。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@OnlyIn(Dist.CLIENT)
public class GlintbladesRender extends EntityRenderer<EntityGlintblades> {

    /**
     * 素材中「剑尖方向」相对贴图 +X 轴的夹角（度，逆时针为正）。
     * <p>详见类注释「斜向素材的轴向补偿」——<b>这是唯一需要按素材调整的常量</b>。</p>
     */
    private static final float TEXTURE_TIP_ANGLE_DEG = 45.0f;

    /** 出现动画时长（tick）：剑从 0 缩放到实际大小，避免凭空「啪」地蹦出来 */
    private static final float APPEAR_TICKS = 5.0f;

    /**
     * 贴图法线方向的加厚倍数（模型空间）。
     * <p>{@code item/generated} 默认挤出厚度为 1/16 格，自转到侧面时太薄。
     * 调大让侧面更实，调到 1.0 即完全保持原版厚度。</p>
     */
    private static final float Z_THICKNESS = 2.5f;

    /** size=1 时的自转速度（度/秒）。体积越大转得越慢，见 {@link #spinSpeedFor} */
    private static final float SPIN_SPEED_BASE = 150.0f;

    /** 飞行期的自转加速倍率：扎出去时转得更急，强化「钻进去」的观感 */
    private static final float SPIN_SPEED_FLYING_MULT = 1.8f;

    /**
     * 渲染器起始墙钟毫秒（类加载时固定）。
     * <p>自转角必须用差值再转 float：直接 {@code currentTimeMillis()/1000f} 数值约 1.7e9，
     * 超出 float 有效精度，逐帧算出的时间完全相同、剑会静止不转。</p>
     */
    private static final long START_MILLIS = System.currentTimeMillis();

    /**
     * 视锥裁剪时包围盒的额外外扩系数（× 剑体积）。
     * <p>
     * 实体注册的碰撞箱恒为 0.75×0.75，而巨剑阵的剑 {@code size} 高达 7.5——
     * 用原始包围盒做视锥判定时，剑身早已占满半个屏幕，包围盒却还是脚下那一小块，
     * 侧对镜头时会被<b>误判为不可见而整把消失</b>。故按实际体积外扩。
     * </p>
     */
    private static final double CULL_INFLATE_FACTOR = 0.8;

    /** 无法确定朝向时的兜底方向：竖直向上（剑立着悬浮） */
    private static final Vec3 FALLBACK_DIRECTION = new Vec3(0.0, 1.0, 0.0);

    protected final Item item;
    private final ItemRenderer itemRenderer;

    /**
     * 渲染用物品堆栈。
     * <p>提为字段复用：本渲染器只画同一个物品，没有任何逐实体差异，
     * 每帧新建纯属浪费（详见类注释「性能」）。</p>
     */
    private final ItemStack renderStack;

    /**
     * 构造函数。
     *
     * @param context 渲染器上下文
     * @param item    用于渲染的物品（魔法辉剑）
     */
    public GlintbladesRender(@Nonnull EntityRendererProvider.Context context,
                             @Nonnull Item item) {
        super(context);
        this.item = item;
        this.itemRenderer = context.getItemRenderer();
        this.renderStack = new ItemStack(item);
    }

    /**
     * 视锥裁剪：按剑的实际体积外扩包围盒，避免巨剑被误裁。
     *
     * @param entity  实体
     * @param frustum 视锥
     * @param camX    相机 X
     * @param camY    相机 Y
     * @param camZ    相机 Z
     * @return 应当渲染返回 true
     */
    @Override
    public boolean shouldRender(@Nonnull EntityGlintblades entity, @Nonnull Frustum frustum,
                                double camX, double camY, double camZ) {
        double inflate = Math.max(0.5, entity.getSize() * CULL_INFLATE_FACTOR);
        AABB box = entity.getBoundingBox().inflate(inflate);
        if (box.hasNaN() || box.getSize() == 0.0) {
            box = new AABB(entity.getX() - inflate, entity.getY() - inflate, entity.getZ() - inflate,
                    entity.getX() + inflate, entity.getY() + inflate, entity.getZ() + inflate);
        }
        return frustum.isVisible(box);
    }

    @Override
    public void render(@Nonnull EntityGlintblades entity,
                       float entityYaw,
                       float partialTicks,
                       @Nonnull PoseStack poseStack,
                       @Nonnull MultiBufferSource buffer,
                       int packedLight) {

        // ===== 1) 取本帧刀身朝向（客户端实时计算，与位置同步频率无关）=====
        Vec3 dir = entity.getAimDirection(partialTicks);
        if (dir == null) {
            dir = FALLBACK_DIRECTION;
        }

        double dx = dir.x;
        double dy = dir.y;
        double dz = dir.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        // 见类注释「姿态数学」：由目标方向反解出 yaw / pitch
        float yawDeg = (float) Math.toDegrees(Math.atan2(-dz, dx));
        float pitchDeg = (float) Math.toDegrees(Math.atan2(dy, horizontal));
        // v4.0：绕刀身轴持续自转（取代原先的 billboard roll）
        float rollDeg = computeSpin(entity, partialTicks);

        // ===== 2) 缩放：实际体积 × 出现动画 =====
        float appear = Mth.clamp((entity.tickCount + partialTicks) / APPEAR_TICKS, 0f, 1f);
        // 缓出，让浮现有「魔法凝聚成形」的加速感而非匀速拉伸
        float appearScale = easeOutCubic(appear);
        float scale = entity.getSize() * appearScale;
        if (scale <= 1.0e-4f) {
            // 尚未成形：不画，同时跳过后续全部三角函数
            return;
        }

        poseStack.pushPose();

        // ===== 3) 平移到视觉中心 =====
        // v4.1：不再简单地「抬半个碰撞箱」，而是取 getRenderCenter 与实体插值位置之差。
        // 原因：挂了悬浮锚点的剑，其渲染位置由「释放者的插值位置 + 插值 yaw」现场算出，
        // 与实体自身按 tick 位置插值出来的坐标并不相同（相差最多一个 tick 的释放者位移）。
        // EntityRenderDispatcher 已经把 poseStack 平移到了实体的插值位置，
        // 故这里补上差值，剑才会与释放者的渲染严格同相位——这正是「转视角时剑发抖」的修复点。
        // 必须在 scale 之前平移，且用未缩放的世界单位，中心位置才与体积无关。
        Vec3 renderCenter = entity.getRenderCenter(partialTicks);
        Vec3 rawPos = entity.getPosition(partialTicks);
        poseStack.translate(
                renderCenter.x - rawPos.x,
                renderCenter.y - rawPos.y,
                renderCenter.z - rawPos.z);
        poseStack.scale(scale, scale, scale);

        // ===== 4) 姿态复合（PoseStack 语义：后调用的先作用于模型）=====
        poseStack.mulPose(Axis.YP.rotationDegrees(yawDeg));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitchDeg));
        poseStack.mulPose(Axis.XP.rotationDegrees(rollDeg));
        // 最内层：把斜向素材的刀身轴掰正到 +X（详见类注释）
        poseStack.mulPose(Axis.ZP.rotationDegrees(-TEXTURE_TIP_ANGLE_DEG));
        // ⚠ 必须是最后一个变换：只有排在最后才是在模型空间沿贴图法线加厚（详见类注释）
        poseStack.scale(1f, 1f, Z_THICKNESS);

        // ===== 5) 渲染物品 =====
        // 用 NONE 而非 THIRD_PERSON_RIGHT_HAND：后者会套用模型 display 里那组
        // rotation/translation（那是给手持姿势准备的），与这里自算的姿态叠加会完全错乱。
        // NONE 走 NO_TRANSFORM，模型以原点为中心、平铺在 XY 平面，正是上面数学的前提。
        this.itemRenderer.renderStatic(
                this.renderStack,
                ItemDisplayContext.NONE,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                entity.level(),
                entity.getId()
        );

        poseStack.popPose();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    /**
     * 求本帧绕刀身轴的自转角（度）。
     * <p>用实体 id 错开初相位，避免一圈剑整齐划一地同步旋转——那会显得像一整块贴图在转，
     * 而不是八把各自悬浮的剑。</p>
     *
     * @param entity       实体
     * @param partialTicks 帧间插值系数（未使用：自转由墙钟驱动，本就连续，无需再插值）
     * @return 自转角（度）
     */
    private static float computeSpin(@Nonnull EntityGlintblades entity, float partialTicks) {
        float seconds = (System.currentTimeMillis() - START_MILLIS) / 1000f;
        float speed = spinSpeedFor(entity.getSize());
        if (entity.isShooted()) {
            speed *= SPIN_SPEED_FLYING_MULT;
        }
        return seconds * speed + entity.getId() * 47f;
    }

    /**
     * 按体积换算自转速度：越大的剑转得越慢。
     * <p>用开方而非线性衰减——线性会让 size=7.5 的巨剑几乎不转（1/7.5），
     * 开方后是 1/2.7，仍看得出在转，但明显比小剑沉。</p>
     *
     * @param size 剑的体积
     * @return 自转速度（度/秒）
     */
    private static float spinSpeedFor(float size) {
        if (size <= 1f) {
            return SPIN_SPEED_BASE;
        }
        return SPIN_SPEED_BASE / (float) Math.sqrt(size);
    }

    /**
     * 缓出（cubic）。
     *
     * @param t 0~1
     * @return 缓出后的值
     */
    private static float easeOutCubic(float t) {
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    @Override
    @Nonnull
    public ResourceLocation getTextureLocation(@Nonnull EntityGlintblades entity) {
        // 实际渲染走物品模型（贴图由 ItemRenderer 自行绑定），本方法的返回值不会被使用。
        // 直接复用方块/物品图集的常量，既省掉一次 ResourceLocation 构造，
        // 也顺带消掉「ResourceLocation(String,String) 已过时」的编译警告。
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
