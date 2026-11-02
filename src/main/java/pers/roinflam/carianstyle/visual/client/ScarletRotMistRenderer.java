package pers.roinflam.carianstyle.visual.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.ScarletRotSyncHandler;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.toggle.VisualEffectType;
import pers.roinflam.carianstyle.visual.toggle.VisualToggle;
import javax.annotation.Nullable;
import java.util.List;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(V2)
 *
 * 猩红腐败「红雾」客户端渲染器（纯客户端自绘，还原艾尔登法环猩红沼泽弥漫的红色孢子雾）。
 * <p>
 * <b>判定采用三重冗余（关键）：</b>原版 {@link MobEffect} 只对「玩家自己」完整同步到客户端；对
 * <b>其他实体</b>（尤其是怪物），原版仅在玩家<b>开始追踪该实体的那一刻</b>同步一次当时的效果列表，此后追踪期间
 * 该实体的效果变化（新加 / 移除 / 过期）<b>不会</b>再同步给观察者。因此战斗中给怪物施加猩红腐败后，
 * 观察者客户端的 {@code entity.hasEffect(SCARLET_ROT)} 仍为 {@code false}。为兼顾「可靠」与
 * 「覆盖战斗中的怪」，本渲染器对每个实体用三个条件取并集判定（见 {@link #shouldRenderRot}）：
 * <ol>
 *     <li>{@code entity.hasEffect(SCARLET_ROT)}——覆盖玩家自己、以及在玩家开始追踪前就已带腐败的实体；</li>
 *     <li>{@link ClientSyncEffectManager#shouldRenderEffect}（序列号 {@link ScarletRotSyncHandler#SCARLET_ROT_SERIAL}）
 *         ——覆盖战斗中被施加腐败的怪；</li>
 *     <li>{@link #hasAeonia}（主手带腐败女神附魔）——腐败女神持有者必带永久腐败，附魔随物品 NBT 可靠同步。</li>
 * </ol>
 * 即便同步链路(2)完全失效，(1)(3)也能退回到「玩家自己 + 腐败女神」的可见水平，不会全黑。
 * <p>
 * <b>腐败女神光环独立判定：</b>感染源光环只看「主手是否带 aeonia」(条件 3)，<b>不嵌套在腐败判定之内</b>，
 * 故无论腐败同步链路如何，腐败女神的大光环必定显示。
 * <p>
 * <b>孢子形状：</b>每颗孢子用「面向相机的圆形径向渐变」绘制（{@link #emitSoftSpore}，中心不透明、边缘
 * 渐隐为 0），放大后呈化开的雾团而非尖锐红点。
 * <p>
 * <b>两类视觉：</b>(1) 普通中毒红雾——脚下水平红雾盘 + 环绕上升的猩红孢子点云；(2) 腐败女神 aeonia
 * 感染源光环——大范围地面腐败浸染盘 + 感染半径内地面各处升腾的孢子柱 +（可选）脚下缓慢旋转的核心孢子环。
 * <p>
 * <b>浓度与淡出：</b>客户端无法可靠取得其他实体效果的等级与剩余时间，故普通红雾统一
 * 基础浓度（{@code amplifier=0}）、不做到期淡出（{@code fade=1}）；出现 / 消失由判定条件控制。
 *
 * <h3>v3（顶点量，近距离视觉零变化）：接入 {@link VisualLod}</h3>
 * <p>
 * 顶点量分两档，<b>腐败女神持有者是全模组单实体第二高</b>（仅次于三重黄金树祝福）：
 * </p>
 * <pre>
 * 普通中毒者：
 *   地面红雾盘（2 层 × 24 段 × 3）        144
 *   上升孢子（16 颗 × 10 段 × 3）         480
 *   ─────────────────────────────────────────
 *   小计                                ~624
 *
 * 腐败女神持有者（在上述基础上叠加）：
 *   浸染盘（3 层 × 32 段 × 3）            288
 *   环境升腾孢子（32 颗 × 10 段 × 3）     960
 *   ─────────────────────────────────────────
 *   合计                               ~1872 顶点 / 实体 / 帧
 * </pre>
 * <p>
 * 且腐败会<b>传播</b>——猩红艾奥尼亚附近死亡的实体有 50% 概率把腐败扩散给 16 格内其他实体，
 * 刷怪塔 / 群体交战时同屏中毒者数量可以相当高，是本模组最容易出现「一大片实体同时带同一种特效」的场景。
 * </p>
 * <p>
 * 现按 {@link VisualLod#detail} 缩放：{@link VisualLod#FULL_DETAIL_RANGE} 格内系数为 1.0，
 * <b>与优化前逐像素一致</b>；40 格外腐败女神持有者降至约 380 顶点（降幅 80%）。
 * </p>
 * <p>
 * <b>感染源孢子必须按步长抽取，不能截断（重要）：</b>环境升腾孢子的分布是
 * {@code ringFrac = (i + 0.5) / 总数}、{@code baseRad = R × sqrt(ringFrac)} 的均匀面积分布——
 * <b>下标 i 越大、半径越靠外</b>。若简单地「只画前 N 颗」，结果是<b>只剩中心一小撮、外圈整个空掉</b>，
 * 大光环会塌成一个小点，视觉完全变形。故改为 {@code for (i = 0; i < 总数; i += step)}，
 * {@code ringFrac} 仍按<b>原始总数</b>计算，保证保留孢子的半径与方位不变、且始终铺满整个感染范围。
 * </p>
 *
 * @author RoinFlam
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class ScarletRotMistRenderer {
    private static final String AEONIA_ENCHANT_ID = "aeonia";
    private static final double CULL = 48.0;
    private static final double CULL_SQR = CULL * CULL;
    private static final float TAU = (float) (Math.PI * 2.0);
    private static final float Y_OFFSET = 0.02f;
    private static final int SPORE_SEGMENTS = 10;
    private static final long START_MILLIS = System.currentTimeMillis();
    private static final int SPORE_SEGMENTS_MIN = 4;
    private static final int DISC_SEGMENTS_MIN = 8;
    private static final float GROUND_OUTER_KEEP_THRESHOLD = 0.55f;
    private static final float AEONIA_OUTER_KEEP_THRESHOLD = 0.6f;
    private static final int SCARLET = 0xE0244A;
    private static final int SCARLET_DEEP = 0x800018;
    private static final int SCARLET_HAZE = 0x5A0012;
    private static final int BASE_SPORES = 16;
    private static final int SPORES_PER_LEVEL = 4;
    private static final int MAX_SPORES = 32;
    private static final float RISE_SPEED = 0.22f;
    private static final float SPORE_SIZE = 0.13f;
    private static final float RISE_HEIGHT_FACTOR = 1.5f;
    private static final float SPREAD_FACTOR = 1.0f;
    private static final float SPORE_BASE_ALPHA = 0.6f;
    private static final int GROUND_LAYERS = 2;
    private static final int GROUND_SEGMENTS = 24;
    private static final float GROUND_RADIUS_BASE = 1.6f;
    private static final float GROUND_RADIUS_STEP = 0.7f;
    private static final float GROUND_BASE_ALPHA = 0.26f;
    private static final float AEONIA_FIELD_RADIUS = 7.0f;
    private static final int AEONIA_GROUND_LAYERS = 3;
    private static final int AEONIA_GROUND_SEGMENTS = 32;
    private static final float AEONIA_GROUND_ALPHA = 0.15f;
    private static final int AEONIA_FIELD_SPORES = 32;
    private static final float AEONIA_RISE_SPEED = 0.16f;
    private static final float AEONIA_SPORE_HEIGHT = 2.6f;
    private static final float AEONIA_SPORE_SIZE = 0.13f;
    private static final float AEONIA_SPORE_ALPHA = 0.58f;
    private static final float AEONIA_SWIRL = 0.6f;
    private static final int AEONIA_CORE_RING_COUNT = 0;
    private static final float AEONIA_CORE_RING_RADIUS = 1.2f;
    private static final float AEONIA_CORE_RING_SPEED = 0.3f;
    private static final float AEONIA_CORE_RING_HEIGHT = 0.15f;
    private static final float AEONIA_CORE_RING_SIZE = 0.14f;
    private ScarletRotMistRenderer() {
    }
}

