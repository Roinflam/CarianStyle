package pers.roinflam.carianstyle.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.LevelReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.dynamicattr.clientsync.ClientSyncStealthRenderer;
import pers.roinflam.carianstyle.network.ClientSyncEffectManager;
import pers.roinflam.carianstyle.network.StealthHitboxSyncHelper;

/**
 * 客户端专用 Mixin：隐身玩家的碰撞箱（F3+B）与影子都不再绘制。
 *
 * <h3>补丁的对象与原因</h3>
 * <p>
 * 原版 {@code EntityRenderDispatcher.render} 在调用完实体渲染器之后，还会单独画两样东西，
 * 各自的判定条件里都只看原版隐身标志：
 * </p>
 * <ul>
 *     <li>影子：{@code entityShadows && shouldRenderShadow && shadowRadius > 0 && !entity.isInvisible()}；</li>
 *     <li>碰撞箱：{@code renderHitBoxes && !entity.isInvisible() && !showOnlyReducedInfo()}。</li>
 * </ul>
 * <p>
 * 本模组的隐身只取消了 {@code RenderPlayerEvent.Pre}（模型不渲染，见 {@link ClientSyncStealthRenderer}），
 * 并不设置原版隐身标志，所以隐身者脚下的影子和 F3+B 的碰撞箱都照画——
 * 有人借碰撞箱在 PvP 里看穿隐身。为什么不改用原版隐身标志、以及同步链路的设计，
 * 见 {@link StealthHitboxSyncHelper}。
 * </p>
 *
 * <h3>补丁做法</h3>
 * <ul>
 *     <li><b>碰撞箱</b>：{@code renderHitbox} 的 HEAD 注入，实体挂着
 *         {@link StealthHitboxSyncHelper#STEALTH_HITBOX_SERIAL}（序列号 19）时取消。
 *         该序列号只在服务端配置 {@code hideStealthHitbox} 打开时下发，客户端不读任何配置。
 *         取消的是整个 {@code renderHitbox}：白色碰撞箱、红色眼高线、蓝色视线向量一并不画。
 *         该方法只在 F3+B 打开时才会被调用。</li>
 *     <li><b>影子</b>：{@code renderShadow} 的 HEAD 注入，实体挂着
 *         {@link ClientSyncStealthRenderer#STEALTH_SERIAL}（序列号 4，与隐藏模型同一个）且是玩家时取消。
 *         只管玩家是因为模型隐藏只作用于玩家——序列号 4 对带隐身的怪物也会下发，
 *         但怪物模型照常可见，影子不能凭空消失。
 *         <b>不受 {@code hideStealthHitbox} 开关控制</b>：影子不用按任何键、所有人都看得见，
 *         模型已经藏了、影子还留在地上属于显示缺陷，不是反作弊选项。
 *         与原版隐身药水的表现一致（原版隐身同样没有影子）。</li>
 * </ul>
 * <p>
 * 两处的判断都是一次 O(1) 的 Set 查找。{@code renderShadow} 只有在影子选项开启、
 * 且实体在 16 格内（原版按 {@code 1 - 距离²/256} 算浓度，大于 0 才调用）时才会被调到，没有性能顾虑。
 * </p>
 *
 * <h3>映射说明</h3>
 * <p>
 * 目标是原版类，未写 {@code remap = false}：方法名用 Mojmap 的 {@code renderHitbox} / {@code renderShadow}，
 * Loom 在 remapJar 时直接把注解字符串改写成 SRG 名 {@code m_114441_} / {@code m_114457_}
 * （jar 里没有 refmap 文件，这是正常的）。描述符里的类名一律是 Mojmap。
 * 两个目标的签名已对照 1.20.1 / Forge 47.4.13 的字节码与客户端 SRG jar 核实：
 * </p>
 * <ul>
 *     <li>{@code private static void renderHitbox(PoseStack, VertexConsumer, Entity, float)}</li>
 *     <li>{@code private static void renderShadow(PoseStack, MultiBufferSource, Entity, float, float, LevelReader, float)}</li>
 * </ul>
 * <p>
 * 目标都是静态方法，所以注入处理器也必须是 {@code static}。
 * </p>
 *
 * <h3>为什么放在 client 列表</h3>
 * <p>
 * {@link EntityRenderDispatcher} 是纯客户端类，物理服务端不存在；
 * 放在 {@code client} 数组中，Mohist 等混合端的服务端根本不会尝试加载本 Mixin。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    /**
     * 注入 {@code EntityRenderDispatcher.renderHitbox} 的 HEAD：隐身玩家不画碰撞箱。
     *
     * @param poseStack    矩阵栈
     * @param buffer       线框顶点缓冲
     * @param entity       正在绘制碰撞箱的实体
     * @param partialTicks 部分 tick
     * @param ci           回调信息（用于取消原版方法）
     */
    @Inject(
            method = "renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void carianstyle$hideStealthHitbox(PoseStack poseStack, VertexConsumer buffer,
                                                      Entity entity, float partialTicks, CallbackInfo ci) {
        if (ClientSyncEffectManager.shouldRenderEffect(
                StealthHitboxSyncHelper.STEALTH_HITBOX_SERIAL, entity.getId())) {
            ci.cancel();
        }
    }

    /**
     * 注入 {@code EntityRenderDispatcher.renderShadow} 的 HEAD：隐身玩家不画影子。
     *
     * @param poseStack    矩阵栈
     * @param buffer       缓冲源
     * @param entity       正在绘制影子的实体
     * @param strength     影子浓度
     * @param partialTicks 部分 tick
     * @param level        世界
     * @param radius       影子半径
     * @param ci           回调信息（用于取消原版方法）
     */
    @Inject(
            method = "renderShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/entity/Entity;FFLnet/minecraft/world/level/LevelReader;F)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void carianstyle$hideStealthShadow(PoseStack poseStack, MultiBufferSource buffer,
                                                      Entity entity, float strength, float partialTicks,
                                                      LevelReader level, float radius, CallbackInfo ci) {
        // 只管玩家：序列号 4 对带隐身的怪物也会下发，但怪物的模型并不隐藏，影子得留着
        if (entity instanceof Player && ClientSyncEffectManager.shouldRenderEffect(
                ClientSyncStealthRenderer.STEALTH_SERIAL, entity.getId())) {
            ci.cancel();
        }
    }
}
