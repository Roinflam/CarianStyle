package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.EnchantmentSlots;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.codex.ForeignCodex;
import pers.roinflam.carianstyle.codex.ItemCategoryNamer;
import pers.roinflam.carianstyle.codex.ModNames;
import pers.roinflam.carianstyle.codex.ForeignMeta;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PORT-STUB(G)
 *
 * 外部附魔（原版与其它模组）的详情面板。
 *
 * <h3>为什么单独写一个而不是复用 {@link CodexDetail}</h3>
 * <p>
 * 两者能展示的信息差得太远：本模组的详情有题材主题、可调数值、宝藏归因、
 * 整类互斥折叠；外部附魔这些一概没有，硬塞进同一个面板就会到处是
 * {@code if (isForeign)}，两种形态互相牵制，改一个要担心另一个。
 * </p>
 * <p>
 * 共用的部分已经抽到 {@link UiTheme}（区块标题、信息行、徽章）和
 * {@link DescriptionText}（描述排版）里，重复的只是几十行调用，值得。
 * </p>
 *
 * <h3>跳转</h3>
 * <p>
 * 冲突列表里既有外部附魔也有本模组附魔，两者都可点击：
 * 前者在本标签页内跳转，后者会切回「附魔百科」标签页。
 * 界面靠键里有没有冒号来区分，见 {@code CodexEntry.getKey()}。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class ForeignDetail {
    private static final int PADDING = 10;
    public void setFilter(@Nullable ItemFilter filter) {
    }
    public void setBounds(int x, int y, int width, int height) {
    }
    public void setMeta(@Nullable ForeignMeta meta) {
    }
    public void render(@Nonnull GuiGraphics g, @Nonnull Font font, int mouseX, int mouseY) {
    }
    @Nullable public String mouseClicked(double mouseX, double mouseY) {
        return "";
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    private static final class HitBox {
        HitBox(int x, int y, int width, int height, String targetKey) {
        }
    }
}

