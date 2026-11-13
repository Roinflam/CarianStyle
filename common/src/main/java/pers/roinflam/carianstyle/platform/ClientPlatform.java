package pers.roinflam.carianstyle.platform;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.List;
import java.util.Optional;

/**
 * 只有客户端才有的平台能力。
 * <p>
 * 方法签名里全是客户端类型，所以<b>只许客户端代码经 {@link Services#client()} 取</b>；
 * 专用服上去取会直接抛异常，不会去加载实现类（实现类引用的客户端类在专用服上不存在）。
 * </p>
 * <p>
 * 前四个是登记类，两类调用时机不一样：
 * </p>
 * <ul>
 *   <li>按键、HUD 层：NeoForge 只认各自注册事件里的登记，实现先记下、到事件里再交出去，而这两个事件发得比
 *       客户端初始化早，所以<b>必须在客户端入口构造时调</b>（NeoForge 上是客户端入口的构造器），
 *       事件发过之后再调会抛异常。</li>
 *   <li>实体渲染器、渲染层：当场登记，在客户端初始化阶段的主线程里调（NeoForge 上是
 *       {@code FMLClientSetupEvent} 的 {@code enqueueWork}）。</li>
 * </ul>
 * <p>
 * Fabric 上四个都当场登记，上面的时机两边都满足。
 * </p>
 * <p>
 * {@link #applyItemTransform}、{@link #renderItemTooltip}、{@link #renderCustomItem} 在 NeoForge 上保留平台行为
 * （别的模组的模型、物品会覆写对应的扩展方法），不统一成原版写法。
 * </p>
 */
public interface ClientPlatform {

    /** 给实体类型登记渲染器。 */
    <T extends Entity> void registerEntityRenderer(EntityType<? extends T> type,
                                                   EntityRendererProvider<T> provider);

    /** 设置方块的渲染层（如 {@code RenderType.cutout()}）。 */
    void setBlockRenderLayer(Block block, RenderType layer);

    /**
     * 登记一个按键，返回按键对象（读按下状态用它）。
     * <p>
     * 不带按键冲突上下文：Fabric 上没有这个概念。NeoForge 实现固定用「游戏中」（{@code KeyConflictContext.IN_GAME}），
     * 和现在唯一的按键一样；以后要别的上下文再加参数。
     * </p>
     *
     * @param name     翻译键，如 {@code key.carianstyle.open_codex}
     * @param keyCode  默认按键（GLFW 键码）
     * @param category 按键设置里的分组翻译键
     */
    KeyMapping registerKeyMapping(String name, int keyCode, String category);

    /** 登记一个画在所有 HUD 层最上面的层。 */
    void registerHudAboveAll(ResourceLocation id, LayeredDraw.Layer layer);

    /** 按物品模型自己的显示变换（第一人称、第三人称、地上……）变换 {@code poseStack}。 */
    void applyItemTransform(BakedModel model, ItemDisplayContext context, PoseStack poseStack,
                            boolean leftHand);

    /** 画物品提示框（带物品本身，别的模组改提示框外观时认得出是哪个物品）。 */
    void renderItemTooltip(GuiGraphics graphics, Font font, List<Component> lines,
                           Optional<TooltipComponent> image, ItemStack stack, int mouseX, int mouseY);

    /**
     * 用物品的自定义渲染器（盾牌、旗帜这类方块实体样式的物品）画一次。
     * <p>
     * 返回值只说明「交给了某个自定义渲染器」，不保证画出了东西：NeoForge 上每个物品都有一个渲染器
     * （普通物品是原版那个，对它什么也不画）。调用方要按实际收到的几何判断画没画，现在截盾牌几何的代码就是这么判的。
     * </p>
     *
     * @return 交给了自定义渲染器就是 true；平台上找不到渲染器就什么也不做、返回 false
     */
    boolean renderCustomItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack,
                             MultiBufferSource buffers, int light, int overlay);

    /** 命名空间对应的模组显示名；没有这个模组就是空。 */
    Optional<String> modDisplayName(String namespace);
}
