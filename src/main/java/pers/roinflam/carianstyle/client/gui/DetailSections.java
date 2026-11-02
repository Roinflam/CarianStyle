package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.ItemFit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * PORT-STUB(G)
 *
 * 两个详情面板共用的区块绘制。
 *
 * <h3>为什么要抽出来</h3>
 * <p>
 * {@link CodexDetail}（本模组附魔）与 {@link ForeignDetail}（原版及其它模组附魔）
 * 各自实现了「适用物品种类」这一块。两份代码<b>几乎</b>一样——同样的标题、同样的图标网格、
 * 同样的脚注——但因为是分两次写的，缩进、间距、颜色、种类名画不画，都出现过不一致。
 * </p>
 * <p>
 * 每修一次都是「把 B 改成和 A 一样」，而下次给其中一边加东西时又会再分叉。
 * 问题不在于某一次改漏了，在于同一块界面存在两份实现。
 * </p>
 * <p>
 * 抽到这里之后，两边传入各自的数据、共用同一段绘制代码，
 * <b>它们不可能再长得不一样</b>。两边信息量的差异（本模组读注解、外部靠实测反推）
 * 收敛成了「传进来的 {@code categoryName} 不同」，仅此而已。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class DetailSections {
    private static final int ICON_STRIDE = 20;
    private static final int ICON_SIZE = 18;
    private DetailSections() {
    }
    public static void idChip(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull String id, int left, int y, int innerWidth, int minLeft) {
    }
    public static int itemTypes(@Nonnull GuiGraphics g, @Nonnull Font font, @Nonnull Component categoryName, @Nonnull List<Item> items, int left, int cursorY, int innerWidth) {
        return 0;
    }
    public static int fitCard(@Nonnull GuiGraphics g, @Nonnull Font font, @Nullable ItemFilter filter, @Nonnull Enchantment enchantment, int left, int cursorY, int innerWidth) {
        return 0;
    }
}

