package pers.roinflam.carianstyle.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.visual.client.ShieldWardRenderer;

/**
 * 补丁目标：{@link ItemInHandRenderer#renderArmWithItem}（<b>第一人称</b>手持物品渲染）。
 * <p>
 * <b>补的是什么：</b>在原版把第一人称手持物品画完之后、姿态栈弹出之前，
 * 追加一层本模组的护盾类附魔特效（魔力盾牌的蓝色魔力护膜、不变盾牌的静止石壁）。
 * </p>
 * <p>
 * 与 {@link MixinItemInHandLayer}（第三人称）配对，两者调用同一个
 * {@link ShieldWardRenderer#renderOnItem}，因此第一 / 第三人称的视觉完全一致，
 * 不存在两套几何要同步维护的问题。
 * </p>
 *
 * <h3>为什么不能用 Forge 的 RenderHandEvent</h3>
 * <p>
 * {@code RenderHandEvent} 在<b>手臂变换之前</b>触发，那时 PoseStack 还停在相机空间，
 * 拿不到盾牌被渲染出来的位置——用它画出来的东西会浮在屏幕正中央，
 * 而不是贴在盾上。所以第一人称同样只能走 Mixin。
 * </p>
 *
 * <h3>⚠ 为什么注入点是 popPose 而不是 TAIL</h3>
 * <p>
 * 这是本补丁最容易踩错的一点。目标方法的结构是：
 * </p>
 * <pre>
 * if (!player.isScoping()) {
 *     poseStack.pushPose();
 *     ... 各分支的手臂 / 物品变换 ...
 *     this.renderItem(...);
 *     poseStack.popPose();   // ← 就在方法末尾
 * }
 * </pre>
 * <p>
 * 也就是说它<b>自己把 push / pop 配平了</b>。若注入 {@code TAIL}，
 * 执行时 {@code popPose} 已经跑完，PoseStack 弹回了相机空间——
 * 特效会画在摄像机原点上，表现为「屏幕正中一团东西」，而不是贴在盾上。
 * </p>
 * <p>
 * 因此注入点取 {@code INVOKE PoseStack.popPose()}（默认 {@code shift = BEFORE}），
 * 此时姿态栈仍在物品 / 手臂空间，正是我们要的。
 * 该方法内 {@code popPose} 只出现一次，不存在选错调用点的问题。
 * </p>
 *
 * <h3>为什么用 popPose 而不是 renderItem 作为锚点</h3>
 * <p>
 * 两者都在正确的位置（{@code renderItem} 之后、{@code popPose} 之前），
 * 但 {@code PoseStack.popPose()} 的描述符是 {@code ()V}——<b>没有参数，
 * 不可能因为映射差异而对不上</b>；而 {@code renderItem} 的参数表里有
 * {@code ItemDisplayContext} 这类跨版本改过名的类型，风险高得多。
 * </p>
 * <p>
 * 另外 {@code renderItem} 在本方法里有<b>两个调用点</b>（弩分支与通用分支），
 * 用它做锚点还要处理 ordinal，反而更啰嗦。
 * </p>
 * <p>
 * <b>{@code remap = false} 是必需的</b>：{@code com.mojang.blaze3d} 下的类
 * 不参与混淆映射，标记 remap 会让 Mixin 去查一个不存在的映射项。
 * </p>
 *
 * <h3>为什么用 @Inject 而不是 @Overwrite / @Redirect</h3>
 * <p>
 * 这是<b>纯追加</b>行为：不改变原版任何逻辑、不吞掉任何调用、
 * 不影响其它模组对同一方法的注入。{@code @Inject} 是这种场景唯一合适的选择。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer {

    /**
     * 在第一人称物品画完、姿态栈弹出之前追加护盾特效。
     * <p>
     * 显示上下文与「是否左手」两个值<b>完全照搬原版在同一方法里的算法</b>：
     * 先由 {@code hand} 与玩家惯用手推出实际手臂，再据此选
     * {@code FIRST_PERSON_RIGHT_HAND} / {@code FIRST_PERSON_LEFT_HAND}，
     * 并把「是否需要镜像」取为 {@code 右手 ? false : true}。
     * 算错这两个值的后果是护膜左右翻转或贴错面。
     * </p>
     * <p>
     * 其余判定（是否带附魔、是否正在举盾、展开度）都在
     * {@link ShieldWardRenderer#renderOnItem} 内部完成——
     * Mixin 类应当尽可能薄，逻辑放在普通类里既好调试也能与第三人称共用。
     * </p>
     *
     * @param player          本地玩家
     * @param partialTicks    分帧
     * @param pitch           俯仰角
     * @param hand            正在渲染的手
     * @param swingProgress   挥击进度
     * @param stack           正在渲染的物品
     * @param equippedProgress 装备切换进度
     * @param poseStack       姿态栈（此处仍在物品 / 手臂空间）
     * @param buffer          缓冲源
     * @param combinedLight   合并光照值（本特效自发光，不使用）
     * @param ci              回调信息（纯追加，不取消原版行为）
     */
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FF"
                    + "Lnet/minecraft/world/InteractionHand;F"
                    + "Lnet/minecraft/world/item/ItemStack;F"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    remap = false
            )
    )
    private void carianstyle$renderShieldWardFirstPerson(AbstractClientPlayer player,
                                                         float partialTicks, float pitch,
                                                         InteractionHand hand, float swingProgress,
                                                         ItemStack stack, float equippedProgress,
                                                         PoseStack poseStack, MultiBufferSource buffer,
                                                         int combinedLight, CallbackInfo ci) {
        // 与原版同一算法：主手取惯用手，副手取其反侧
        HumanoidArm arm = (hand == InteractionHand.MAIN_HAND)
                ? player.getMainArm()
                : player.getMainArm().getOpposite();
        boolean rightArm = arm == HumanoidArm.RIGHT;
        ItemDisplayContext displayContext = rightArm
                ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        // 原版传给 renderItem 的 leftHand 参数即 !rightArm，此处保持一致
        ShieldWardRenderer.renderOnItem(player, stack, displayContext,
                !rightArm, poseStack, buffer);
    }
}
