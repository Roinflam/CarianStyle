package pers.roinflam.carianstyle.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.base.enchantment.LevelDecay;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.ModNames;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * PORT-STUB(G)
 *
 * 百科右栏：单个附魔的详情。
 *
 * <h3>两遍绘制的原因</h3>
 * <p>
 * 详情内容高度不固定（描述长度、适用物品数量、冲突数量、数值条目数都不一样），
 * 而滚动条需要知道内容总高度。这里的做法是：绘制过程中<b>累加</b> y 偏移，
 * 一遍下来既画完了也算出了总高，下一帧的滚动条就用上一帧算出的高度。
 * 内容在两帧之间不会变，所以看不出差异；比预先算一遍再画一遍省掉一半开销。
 * </p>
 *
 * <h3>冲突跳转</h3>
 * <p>
 * 冲突附魔画成可点击的徽章。命中区域在绘制时记入 {@link #conflictHits}，
 * {@link #mouseClicked} 直接查这张表——不需要在点击时重新推算布局，
 * 也就不会出现「画的位置和点的位置对不上」这种经典 GUI bug。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class CodexDetail {
    private static final int PADDING = 10;
    public void setFilter(@Nullable ItemFilter filter) {
    }
    public void setBounds(int x, int y, int width, int height) {
    }
    public void setMeta(@Nullable EnchantmentMeta meta) {
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
        HitBox(int x, int y, int width, int height, String targetId) {
        }
    }
}

