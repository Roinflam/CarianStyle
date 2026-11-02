package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.RenderSpecificHandEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.client.ShieldWardRenderer;

import javax.annotation.Nonnull;

/**
 * 第一人称手持盾牌上的护鞘（客户端）
 * <p>
 * 1.20.1 用 mixin 在 {@code ItemInHandRenderer} 画完第一人称手持物品、{@code PoseStack.popPose} 之前调
 * {@link ShieldWardRenderer#renderOnItem}。1.12.2 的第一人称手部渲染只有一个事件
 * {@link RenderSpecificHandEvent}（在画之前发，取消就不画），没有「画完之后」的钩子。所以这里：
 * </p>
 * <ol>
 *     <li>订阅这个事件，优先级最低、跳过已被别人取消的。手上是盾牌（{@link ItemShield} 及其子类）
 *         且护鞘生效时，取消事件，自己调公开的
 *         {@code ItemRenderer.renderItemInFirstPerson(player, partialTicks, pitch, hand, swing, stack, equip)}
 *         按原版画盾牌。这个单手版本不会再发事件，不会递归；</li>
 *     <li>再 {@code pushMatrix}，照原版这一路的变换重做一遍：格挡中（{@link EnumAction#BLOCK}）只做
 *         {@code transformSideFirstPerson}；没在用就先做挥动位移，再 {@code transformSideFirstPerson} +
 *         {@code transformFirstPerson}。这两个是 {@code ItemRenderer} 的私有方法，下面照字节码里的常量抄了一份；</li>
 *     <li>走到原版即将对物品调 {@code handleCameraTransforms} 的位置，调 {@code renderOnItem} 画护鞘
 *         （模型变换由它自己做），{@code popMatrix}。</li>
 * </ol>
 * <p>
 * 和 1.20.1 一样，只在手持物品渲染时画，物品栏、掉落物不受影响。装了改第一人称手部渲染的模组时，
 * 原版盾牌的位置和这里重做的变换可能对不上，护鞘会错位。
 * </p>
 * <p>
 * 本类调用 {@code visual/client/ShieldWardRenderer} 的两个公开静态入口（{@code renderOnItem}、{@code hasWard}），
 * 这是 1.20.1 mixin 调同一个入口的对应写法。
 * </p>
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
public final class ShieldWardFirstPersonHook {

    private ShieldWardFirstPersonHook() {
    }

    /**
     * 第一人称画某只手之前：手上是带护鞘附魔的盾牌就接管这只手的渲染
     *
     * @param event 第一人称单手渲染事件
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderHand(RenderSpecificHandEvent event) {
        if (event.isCanceled()) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemShield) || !ShieldWardRenderer.hasWard(stack)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = mc.player;
        if (player == null) {
            return;
        }
        EnumHand hand = event.getHand();
        boolean using = player.isHandActive() && player.getItemInUseCount() > 0 && player.getActiveHand() == hand;
        if (using) {
            EnumAction action = stack.getItemUseAction();
            if (action != EnumAction.BLOCK && action != EnumAction.NONE) {
                // 盾牌只会走格挡；别的用法（模组盾牌改了用法）原版变换不同，不接管，交给原版
                return;
            }
        }

        event.setCanceled(true);
        float partialTicks = event.getPartialTicks();
        float swing = event.getSwingProgress();
        float equip = event.getEquipProgress();
        // 原版画盾牌（单手版本，不会再发 RenderSpecificHandEvent）
        mc.getItemRenderer().renderItemInFirstPerson(player, partialTicks, event.getInterpolatedPitch(),
                hand, swing, stack, equip);

        EnumHandSide side = hand == EnumHand.MAIN_HAND ? player.getPrimaryHand() : player.getPrimaryHand().opposite();
        boolean right = side == EnumHandSide.RIGHT;
        GlStateManager.pushMatrix();
        try {
            if (using) {
                // 格挡中：原版只做这一步
                transformSideFirstPerson(side, equip);
            } else {
                // 没在用：挥动位移 + 两步手持变换，和原版一致
                float f = -0.4F * MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
                float f1 = 0.2F * MathHelper.sin(MathHelper.sqrt(swing) * ((float) Math.PI * 2F));
                float f2 = -0.2F * MathHelper.sin(swing * (float) Math.PI);
                int i = right ? 1 : -1;
                GlStateManager.translate((float) i * f, f1, f2);
                transformSideFirstPerson(side, equip);
                transformFirstPerson(side, swing);
            }
            ShieldWardRenderer.renderOnItem(player, stack,
                    right ? ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND
                            : ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND,
                    !right);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /**
     * 照 {@code ItemRenderer.transformSideFirstPerson}（私有）：手持物品的基准位置，装备动画往下压
     */
    private static void transformSideFirstPerson(@Nonnull EnumHandSide side, float equip) {
        int i = side == EnumHandSide.RIGHT ? 1 : -1;
        GlStateManager.translate((float) i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
    }

    /**
     * 照 {@code ItemRenderer.transformFirstPerson}（私有）：挥动时的旋转
     */
    private static void transformFirstPerson(@Nonnull EnumHandSide side, float swing) {
        int i = side == EnumHandSide.RIGHT ? 1 : -1;
        float f = MathHelper.sin(swing * swing * (float) Math.PI);
        GlStateManager.rotate((float) i * (45.0F + f * -20.0F), 0.0F, 1.0F, 0.0F);
        float f1 = MathHelper.sin(MathHelper.sqrt(swing) * (float) Math.PI);
        GlStateManager.rotate((float) i * f1 * -20.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(f1 * -80.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate((float) i * -45.0F, 0.0F, 1.0F, 0.0F);
    }
}
