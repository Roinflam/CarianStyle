package pers.roinflam.carianstyle.mc112.hook;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerHeldItem;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import pers.roinflam.carianstyle.visual.client.ShieldWardRenderer;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * 第三人称手持盾牌上的护鞘（客户端渲染层）
 * <p>
 * 1.20.1 用 mixin 在 {@code ItemInHandLayer} 画完手持物品、{@code PoseStack.popPose} 之前调
 * {@link ShieldWardRenderer#renderOnItem}。1.12.2 不用 mixin：本类继承原版的 {@link LayerHeldItem}
 * （构造器 public，{@code translateToHand} 是 protected，可以直接复用），作为一个额外的渲染层挂到
 * 用双足模型的生物渲染器上，变换照 {@code LayerHeldItem.renderHeldItem} 一步不差地重做一遍，
 * 走到原版即将画物品的那个位置时调 {@code renderOnItem}。物品本身仍由原版的 {@code LayerHeldItem} 画，
 * 这里只画护鞘。只对 {@link ItemShield} 及其子类、且护鞘当前生效（{@link ShieldWardRenderer#hasWard}）时画。
 * </p>
 * <p>
 * <b>懒装</b>：{@code FMLClientHandler.finishMinecraftLoading} 先跑完 init / postInit，之后才把别的模组用旧接口
 * 注册的渲染器塞进 {@code entityRenderMap}，在 init 里遍历会漏掉它们。所以由 {@link Installer} 在第一次
 * {@code GuiOpenEvent}（主菜单）或第一次进世界后的 {@code ClientTickEvent} 时遍历一次，
 * 用一个集合记已经装过的渲染器，防重复。装到两个玩家渲染器（{@code getSkinMap()}），
 * 以及 {@code entityRenderMap} 里所有主模型是 {@link ModelBiped} 的 {@link RenderLivingBase}。
 * </p>
 * <p>
 * 本类调用 {@code visual/client/ShieldWardRenderer} 的两个公开静态入口（{@code renderOnItem}、{@code hasWard}），
 * 这是 1.20.1 mixin 调同一个入口的对应写法。
 * </p>
 */
@SideOnly(Side.CLIENT)
public final class ShieldWardLayer extends LayerHeldItem {

    /**
     * @param renderer 挂在哪个生物渲染器上（主模型必须是 {@link ModelBiped}）
     */
    public ShieldWardLayer(@Nonnull RenderLivingBase<?> renderer) {
        super(renderer);
    }

    /**
     * 照 {@code LayerHeldItem.doRenderLayer}：左右手分开，幼体先缩小
     */
    @Override
    public void doRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        boolean rightPrimary = entity.getPrimaryHand() == EnumHandSide.RIGHT;
        ItemStack leftStack = rightPrimary ? entity.getHeldItemOffhand() : entity.getHeldItemMainhand();
        ItemStack rightStack = rightPrimary ? entity.getHeldItemMainhand() : entity.getHeldItemOffhand();
        boolean drawRight = isWardShield(rightStack);
        boolean drawLeft = isWardShield(leftStack);
        if (!drawRight && !drawLeft) {
            return;
        }
        GlStateManager.pushMatrix();
        try {
            if (this.livingEntityRenderer.getMainModel().isChild) {
                GlStateManager.translate(0.0F, 0.75F, 0.0F);
                GlStateManager.scale(0.5F, 0.5F, 0.5F);
            }
            if (drawRight) {
                renderWard(entity, rightStack, ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND, EnumHandSide.RIGHT);
            }
            if (drawLeft) {
                renderWard(entity, leftStack, ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND, EnumHandSide.LEFT);
            }
        } finally {
            GlStateManager.popMatrix();
        }
    }

    /**
     * 照 {@code LayerHeldItem.renderHeldItem} 的变换走到手上，再画护鞘
     */
    private void renderWard(@Nonnull EntityLivingBase entity, @Nonnull ItemStack stack,
                            @Nonnull ItemCameraTransforms.TransformType transform, @Nonnull EnumHandSide side) {
        GlStateManager.pushMatrix();
        try {
            if (entity.isSneaking()) {
                GlStateManager.translate(0.0F, 0.2F, 0.0F);
            }
            this.translateToHand(side);
            GlStateManager.rotate(-90.0F, 1.0F, 0.0F, 0.0F);
            GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
            boolean left = side == EnumHandSide.LEFT;
            GlStateManager.translate((float) (left ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            // 原版接下来是 renderItemSide → handleCameraTransforms → translate(-0.5) → 画物品；
            // 这两步模型变换由 renderOnItem 自己做（和 1.20.1 的 applyTransform 一一对应）
            ShieldWardRenderer.renderOnItem(entity, stack, transform, left);
        } finally {
            GlStateManager.popMatrix();
        }
    }

    private static boolean isWardShield(@Nonnull ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemShield && ShieldWardRenderer.hasWard(stack);
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }

    /**
     * 懒装：主菜单出现或第一次进世界时把渲染层挂上去（只遍历一次，之后每次只看一个布尔量）
     */
    @SideOnly(Side.CLIENT)
    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class Installer {

        /**
         * 已经装过的渲染器（按身份）
         */
        private static final Set<Render<?>> INSTALLED = Collections.newSetFromMap(new IdentityHashMap<Render<?>, Boolean>());

        private static boolean done = false;

        private Installer() {
        }

        /**
         * 第一次打开任何界面（启动后第一个就是主菜单）时装
         *
         * @param event 打开界面事件
         */
        @SubscribeEvent
        public static void onGuiOpen(GuiOpenEvent event) {
            if (!done) {
                install();
            }
        }

        /**
         * 兜底：没经过界面就进了世界（例如启动参数直连服务器）时，第一个客户端 tick 装
         *
         * @param event 客户端 tick
         */
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (!done && event.phase == TickEvent.Phase.END && Minecraft.getMinecraft().world != null) {
                install();
            }
        }

        private static void install() {
            RenderManager manager = Minecraft.getMinecraft().getRenderManager();
            if (manager == null) {
                return;
            }
            done = true;
            int count = 0;
            for (RenderPlayer render : manager.getSkinMap().values()) {
                if (add(render)) {
                    count++;
                }
            }
            for (Render<?> render : manager.entityRenderMap.values()) {
                if (render instanceof RenderLivingBase
                        && ((RenderLivingBase<?>) render).getMainModel() instanceof ModelBiped
                        && add((RenderLivingBase<?>) render)) {
                    count++;
                }
            }
            LogUtil.debug("卡利亚式附魔 - 护鞘渲染层已挂到 " + count + " 个渲染器上");
        }

        private static boolean add(@Nonnull RenderLivingBase<?> render) {
            if (!INSTALLED.add(render)) {
                return false;
            }
            render.addLayer(new ShieldWardLayer(render));
            return true;
        }
    }
}
