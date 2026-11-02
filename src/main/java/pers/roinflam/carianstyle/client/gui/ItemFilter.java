package pers.roinflam.carianstyle.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import pers.roinflam.carianstyle.codex.CodexEntry;
import pers.roinflam.carianstyle.codex.EnchantmentCodex;
import pers.roinflam.carianstyle.codex.EnchantmentMeta;
import pers.roinflam.carianstyle.codex.ForeignCodex;
import pers.roinflam.carianstyle.codex.ForeignMeta;
import pers.roinflam.carianstyle.codex.ItemFit;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * PORT-STUB(G)
 *
 * 百科的「放入物品」筛选：玩家从背包里挑一件东西放上去，列表只留下与它有关的附魔。
 *
 * <h3>放上去的是副本</h3>
 * <p>
 * 百科是普通 {@code Screen}，不是容器界面，服务端根本不知道它开着。
 * 这里拿的是客户端背包里那一格的<b>副本</b>，只用来判定，不移动、不改动任何物品，
 * 也就不需要任何网络包——不存在刷物品或者和服务端对不上的问题。
 * </p>
 *
 * <h3>记住的是格子，不是物品</h3>
 * <p>
 * 关掉百科再打开时，筛选按「上次是哪一格、那一格还是不是同一种东西」恢复，
 * 并且重新读那一格的<b>当前</b>内容。典型用法是：看完能附什么 → 关界面去铁砧附一个 →
 * 回来接着看。若恢复成当初那份副本，刚附上的附魔不会出现，冲突也不会更新，
 * 玩家会以为没附成功。
 * </p>
 *
 * <h3>分组</h3>
 * <p>
 * 筛选时列表按「可以附上 / 铁砧过于昂贵 / 已经有了 / 被已有附魔挡住」分组，组内保持百科原有顺序。
 * 原来的题材分组在这时让位：玩家此刻问的是「这件东西还能附什么」，
 * 答案应该是一眼看到的第一组，而不是散在七八个题材里。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@OnlyIn(Dist.CLIENT)
@Deprecated
public final class ItemFilter {
    public void set(@Nonnull ItemStack source, int slot) {
    }
    public void clear() {
    }
    public void restore(@Nonnull Inventory inventory) {
    }
    public boolean follow(@Nonnull Inventory inventory) {
        return false;
    }
    public void reevaluate() {
    }
    public boolean isActive() {
        return false;
    }
    @Nullable public ItemStack getStack() {
        return null;
    }
    public int getSourceSlot() {
        return 0;
    }
    public boolean isStackMultiple() {
        return false;
    }
    public int getBaseRepairCost() {
        return 0;
    }
    public int getFitCount() {
        return 0;
    }
    public int getExpensiveCount() {
        return 0;
    }
    public int getBlockedCount() {
        return 0;
    }
    @Nullable public ItemFit fitOf(@Nonnull Enchantment enchantment) {
        return null;
    }
    public static int countUsable(@Nonnull ItemStack candidate) {
        return 0;
    }
    @Nonnull public List<? extends CodexEntry> apply(@Nonnull List<? extends CodexEntry> entries) {
        return entries;
    }

    private enum Group {
;

        Group(String langKey, int color) {
        }
    }

    private static final class FitEntry implements CodexEntry {
        private FitEntry(@Nonnull CodexEntry delegate, @Nonnull ItemFit fit, @Nonnull Group group, @Nonnull Component groupName) {
        }
        @Nonnull public String getKey() {
            return "";
        }
        @Nonnull public Component getDisplayName() {
            return null;
        }
        public int getMaxLevel() {
            return 0;
        }
        @Nonnull public Component getRarityName() {
            return null;
        }
        public int getRarityColor() {
            return 0;
        }
        public int getRarityOrder() {
            return 0;
        }
        public int getConflictCount() {
            return 0;
        }
        @Nonnull public String getGroupKey() {
            return "";
        }
        @Nonnull public Component getGroupName() {
            return null;
        }
        public int getGroupColor() {
            return 0;
        }
        public boolean matches(@Nullable String lowerKeyword) {
            return false;
        }
        @Nonnull public Enchantment getEnchantment() {
            return null;
        }
        @Nullable public Component getRowNote() {
            return null;
        }
        public int getRowNoteColor() {
            return 0;
        }
        public boolean isRowDimmed() {
            return false;
        }
    }
}

