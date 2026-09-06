package pers.roinflam.carianstyle.codex;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 由「实测能附在哪些物品上」反推出一句人话的物品种类。
 *
 * <h3>为什么要反推</h3>
 * <p>
 * 本模组的附魔能直接读注解里的 {@code type} / {@code customType}，
 * 一句「剑」「胸甲」就说清了。原版和别家模组没有这个注解，
 * 于是「其他附魔」页只能摆一排图标——玩家得自己认图标、自己归纳，
 * 而且看到一把钻石剑也判断不出「是只能附剑，还是剑斧都行」。
 * </p>
 * <p>
 * 这里改为把 {@link ItemProbe} 的实测结果归成几类再拼成一句话。
 * 反推的依据是附魔台真正用的 {@code canEnchant}，
 * 所以结论不会和游戏内的实际行为对不上。
 * </p>
 *
 * <h3>为什么是「归类拼接」而不是「匹配模板」</h3>
 * <p>
 * 一开始想写成一张表：{@code {剑} -> 剑}、{@code {剑,斧} -> 武器}……
 * 但别家模组的附魔可以适用于任意组合，模板匹配一旦落空就只能显示「未知」，
 * 恰恰在最需要说明的时候失效。
 * </p>
 * <p>
 * 归类拼接没有这个问题：认识的部分照常说清，不认识的组合也能如实列出来。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class ItemCategoryNamer {

    /** 四个护甲部位，用于判断是否「全部部位」 */
    private static final Item[] ARMOR_PIECES = {
            Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS
    };

    /** 工具类物品，数量达到阈值就概括成「工具」 */
    private static final Item[] TOOLS = {
            Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SHOVEL,
            Items.DIAMOND_HOE, Items.SHEARS, Items.FLINT_AND_STEEL
    };

    /**
     * 单个物品到「种类名语言键后缀」的对照。
     * <p>用 {@link LinkedHashMap} 保证输出顺序稳定——同一个附魔每次打开，
     * 种类文字必须一模一样，否则会让人以为数据在变。</p>
     */
    private static final Map<Item, String> SINGLE = new LinkedHashMap<>();

    static {
        SINGLE.put(Items.DIAMOND_SWORD, "sword");
        SINGLE.put(Items.DIAMOND_AXE, "axe");
        SINGLE.put(Items.DIAMOND_PICKAXE, "pickaxe");
        SINGLE.put(Items.DIAMOND_SHOVEL, "shovel");
        SINGLE.put(Items.DIAMOND_HOE, "hoe");
        SINGLE.put(Items.SHEARS, "shears");
        SINGLE.put(Items.FLINT_AND_STEEL, "flint_and_steel");
        SINGLE.put(Items.BOW, "bow");
        SINGLE.put(Items.CROSSBOW, "crossbow");
        SINGLE.put(Items.TRIDENT, "trident");
        SINGLE.put(Items.FISHING_ROD, "fishing_rod");
        SINGLE.put(Items.SHIELD, "shield");
        SINGLE.put(Items.ELYTRA, "elytra");
        SINGLE.put(Items.CARVED_PUMPKIN, "pumpkin");
        SINGLE.put(Items.DIAMOND_HELMET, "helmet");
        SINGLE.put(Items.DIAMOND_CHESTPLATE, "chestplate");
        SINGLE.put(Items.DIAMOND_LEGGINGS, "leggings");
        SINGLE.put(Items.DIAMOND_BOOTS, "boots");
    }

    /** 工具数量达到几个就概括成「工具」 */
    private static final int TOOL_THRESHOLD = 3;

    /**
     * 命中的种类数达到这个值就概括成「任意可附魔的物品」。
     * <p>
     * 「耐久」这类通用附魔几乎什么都能附，逐个列出来是
     * 「护甲（全部部位）、工具、剑、弓、弩、三叉戟、钓鱼竿、盾牌、鞘翅」——
     * 九个词一行放不下，读完还是那句「什么都行」。不如直接说那句。
     * </p>
     */
    private static final int GENERIC_THRESHOLD = 7;

    private ItemCategoryNamer() {
    }

    /**
     * 由实测可附魔的物品列表生成种类描述。
     *
     * @param items 实测结果
     * @return 一句人话；列表为空时返回「无」
     */
    @Nonnull
    public static Component describe(@Nonnull List<Item> items) {
        if (items.isEmpty()) {
            return Component.translatable("carianstyle.codex.itemtype.none");
        }

        List<Component> parts = new ArrayList<>();

        // 0) 护甲：四件齐全就概括，否则逐个列出部位
        List<String> armor = new ArrayList<>();
        for (Item piece : ARMOR_PIECES) {
            if (items.contains(piece)) {
                armor.add(SINGLE.get(piece));
            }
        }
        boolean fullArmor = armor.size() == ARMOR_PIECES.length;
        if (fullArmor) {
            parts.add(Component.translatable("carianstyle.codex.itemtype.armor_full"));
        } else {
            for (String key : armor) {
                parts.add(name(key));
            }
        }
        // 海龟壳单独提一句：它是唯一不属于钻石护甲那一套的头部装备，
        // 有些附魔（比如水下呼吸）在它身上有特殊意义
        if (items.contains(Items.TURTLE_HELMET) && !items.contains(Items.DIAMOND_HELMET)) {
            parts.add(name("turtle_helmet"));
        }

        // 2) 剑排在工具之前。
        //    钻石斧既是武器也是工具，被归进了工具组，若不先处理剑，
        //    「锋利」就会显示成「斧、剑」——武器附魔却把斧摆在前面，读着别扭
        if (items.contains(Items.DIAMOND_SWORD)) {
            parts.add(name("sword"));
        }

        // 3) 工具：够多就概括成「工具」，少数几个则如实列出
        List<String> tools = new ArrayList<>();
        for (Item tool : TOOLS) {
            if (items.contains(tool)) {
                tools.add(SINGLE.get(tool));
            }
        }
        if (tools.size() >= TOOL_THRESHOLD) {
            parts.add(Component.translatable("carianstyle.codex.itemtype.tools"));
        } else {
            for (String key : tools) {
                parts.add(name(key));
            }
        }

        // 4) 其余单品按固定顺序追加
        for (Map.Entry<Item, String> entry : SINGLE.entrySet()) {
            Item item = entry.getKey();
            if (item == Items.DIAMOND_SWORD || isArmor(item) || isTool(item)
                    || !items.contains(item)) {
                continue;
            }
            parts.add(name(entry.getValue()));
        }

        if (parts.isEmpty()) {
            return Component.translatable("carianstyle.codex.itemtype.other");
        }
        if (parts.size() >= GENERIC_THRESHOLD) {
            return Component.translatable("carianstyle.codex.itemtype.generic");
        }

        MutableComponent result = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                result.append(Component.translatable("carianstyle.codex.itemtype.separator"));
            }
            result.append(parts.get(i));
        }
        return result;
    }

    /**
     * 取某个种类的显示名。
     *
     * @param key 语言键后缀
     * @return 显示名
     */
    @Nonnull
    private static Component name(@Nonnull String key) {
        return Component.translatable("carianstyle.codex.itemtype." + key);
    }

    /**
     * @param item 物品
     * @return 是否属于已在护甲段处理过的物品
     */
    private static boolean isArmor(@Nonnull Item item) {
        for (Item piece : ARMOR_PIECES) {
            if (piece == item) {
                return true;
            }
        }
        return item == Items.TURTLE_HELMET;
    }

    /**
     * @param item 物品
     * @return 是否属于已在工具段处理过的物品
     */
    private static boolean isTool(@Nonnull Item item) {
        for (Item tool : TOOLS) {
            if (tool == item) {
                return true;
            }
        }
        return false;
    }
}
