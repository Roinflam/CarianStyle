package pers.roinflam.carianstyle.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pers.roinflam.carianstyle.visual.client.ShieldWardRenderer;

/**
 * 补丁目标：{@link ItemInHandLayer#renderArmWithItem}（第三人称手持物品渲染层）。
 * <p>
 * <b>补的是什么：</b>在原版把手持物品画完之后、姿态栈弹出之前，
 * 追加一层本模组的护盾类附魔特效
 * （魔力盾牌的蓝色魔力护鞘、不变盾牌的静止石鞘）。
 * </p>
 * <p>
 * 与 {@link MixinItemInHandRenderer}（第一人称）配对，两者调用同一个
 * {@link ShieldWardRenderer#renderOnItem}，因此第一 / 第三人称的视觉完全一致，
 * 不存在两套几何要同步维护的问题。
 * </p>
 *
 * <h3>为什么必须用 Mixin</h3>
 * <p>
 * Forge 为第一人称提供了 {@code RenderHandEvent}，但<b>第三人称的手持物品渲染没有任何事件</b>——
 * {@code ItemInHandLayer} 是 {@code LivingEntityRenderer} 的一个内部图层，
 * 既不发事件也不可替换。要在盾牌<b>自己的局部空间</b>里画东西，只能注入这里。
 * </p>
 * <p>
 * <b>而局部空间正是全部意义所在。</b>此前的实现是在世界空间按「玩家坐标 + 头部朝向」
 * 算一个平面，护膜跟着<b>视线</b>转而不是跟着盾——低头时它往下歪，挥手时它纹丝不动。
 * 注入到这里之后，PoseStack 已经包含了全部手臂动画（举盾、挥击、行走摆臂、受击踉跄），
 * 护鞘自动跟随，一个不落。
 * </p>
 *
 * <h3>⚠ 为什么注入点是 popPose 而不是 TAIL（v1.0 的 bug 就在这里）</h3>
 * <p>
 * <b>v1.0 注入 {@code TAIL}，导致第三人称的护鞘完全歪掉。</b>
 * 记下来的原因是错的，原话是「TAIL 处 PoseStack 仍在手臂空间，
 * 因为原版在方法内部对物品变换所做的 push/pop 是配平的」——推理正好反了。
 * </p>
 * <p>
 * 目标方法在 1.20.1 的结构是：
 * </p>
 * <pre>
 * if (!stack.isEmpty()) {
 *     poseStack.pushPose();
 *     this.getParentModel().translateToHand(arm, poseStack);
 *     poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
 *     poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
 *     boolean flag = arm == HumanoidArm.LEFT;
 *     poseStack.translate((flag ? -1 : 1) / 16.0F, 0.125F, -0.625F);
 *     this.itemRenderer.renderStatic(entity, stack, displayContext, flag, poseStack, ...);
 *     poseStack.popPose();   // ← 就在方法末尾，方法体<b>内部</b>
 * }
 * </pre>
 * <p>
 * <b>正因为 push / pop 是配平的，TAIL 才落在实体模型空间</b>——
 * {@code popPose} 执行完，全部手臂变换（{@code translateToHand} 与那三次
 * {@code mulPose} / {@code translate}）都已经被弹掉了。
 * 在那里画护鞘，它会贴在实体根部并跟着身体偏航转，表现就是「歪的」。
 * </p>
 * <p>
 * 因此注入点取 {@code INVOKE PoseStack.popPose()}（默认 {@code shift = BEFORE}），
 * 此时姿态栈仍在手臂空间，正是 {@link ShieldWardRenderer#renderOnItem} 期待的输入——
 * 它会在内部自己再套一次物品模型变换，从手臂空间落到盾牌上。
 * 该方法内 {@code popPose} 只出现一次，不存在选错调用点的问题。
 * </p>
 * <p>
 * <b>为什么不用 {@code renderStatic} 作锚点：</b>它就在 {@code popPose} 前一行，
 * 位置等价，但参数表里有 {@code ItemDisplayContext} 这类跨版本改过名的类型；
 * 而 {@code PoseStack.popPose()} 的描述符是 {@code ()V}——<b>没有参数，
 * 不可能因为映射差异而对不上</b>。第一人称那份补丁出于同样的理由选了 popPose，
 * 两边保持一致。
 * </p>
 * <p>
 * <b>{@code remap = false} 是必需的</b>：{@code com.mojang.blaze3d} 下的类
 * 不参与混淆映射，标记 remap 会让 Mixin 去查一个不存在的映射项。
 * </p>
 *
 * <h3>为什么不注入 HEAD</h3>
 * <p>
 * 特效是半透明的，必须画在盾牌模型<b>之后</b>才能正确地叠在它上面。
 * 注入 HEAD 会让盾牌覆盖掉护鞘。
 * </p>
 *
 * <h3>为什么用 @Inject 而不是 @Overwrite / @Redirect</h3>
 * <p>
 * 这是<b>纯追加</b>行为：不改变原版任何逻辑、不吞掉任何调用、不影响其它模组对同一方法的注入。
 * {@code @Inject} 是这种场景唯一合适的选择，兼容性最好。
 * </p>
 *
 * <h3>⚠ 目标方法签名</h3>
 * <p>
 * {@code method} 里写的是完整描述符而非裸方法名，这样一旦签名对不上会在<b>加载期立刻报错</b>
 * （配置里 {@code defaultRequire = 1}），而不是安静地不生效——后者要难查得多。
 * </p>
 * <p>
 * 该签名按 <b>Minecraft 1.20.1 官方（Mojang）映射</b>书写。
 * 1.20 起原版把第三个参数的类型从 {@code ItemTransforms.TransformType}
 * 改成了 {@code ItemDisplayContext}，此处用的是后者。
 * </p>
 * <p>
 * <b>若启动时报 mixin apply 失败</b>，几乎可以肯定是这一行描述符与你的映射不符。
 * 请在 IDE 里打开 {@code ItemInHandLayer} 核对
 * {@code renderArmWithItem} 的真实参数表，并同步修改
 * {@code method} 字符串与下方处理方法的形参——两处必须一致。
 * </p>
 *
 * @author FlameForge
 * @version 1.1
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer {

    /**
     * 在原版画完手持物品之后、姿态栈弹出之前，追加护盾特效。
     * <p>
     * 本方法只做一次转发，全部判定（是否带附魔、是否正在举盾、展开度）
     * 都在 {@link ShieldWardRenderer#renderOnItem} 内部完成——
     * Mixin 类应当尽可能薄，逻辑放在普通类里既好调试也能与第一人称共用。
     * </p>
     * <p>
     * {@code displayContext} 直接透传原版参数；「是否左手」取
     * {@code arm == HumanoidArm.LEFT}，与原版传给
     * {@code renderStatic} 的局部变量 {@code flag} 完全一致。
     * 这两个值决定物品模型变换是否镜像，算错会让护鞘左右翻转或贴错面。
     * </p>
     *
     * @param entity         持有者
     * @param stack          正在渲染的物品
     * @param displayContext 显示上下文（第三人称左 / 右手等）
     * @param arm            渲染的是哪只手臂
     * @param poseStack      姿态栈（此处仍在手臂空间）
     * @param buffer         缓冲源
     * @param combinedLight  合并光照值（本特效自发光，不使用）
     * @param ci             回调信息（纯追加，不取消原版行为）
     */
    @Inject(
            method = "renderArmWithItem(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;"
                    + "Lnet/minecraft/world/entity/HumanoidArm;"
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V",
                    remap = false
            )
    )
    private void carianstyle$renderShieldWard(LivingEntity entity, ItemStack stack,
                                              ItemDisplayContext displayContext, HumanoidArm arm,
                                              PoseStack poseStack, MultiBufferSource buffer,
                                              int combinedLight, CallbackInfo ci) {
        ShieldWardRenderer.renderOnItem(entity, stack, displayContext,
                arm == HumanoidArm.LEFT, poseStack, buffer);
    }
}
