package pers.roinflam.carianstyle.codex;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.Items;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * PORT-STUB(G)
 *
 * 「这个附魔能附在什么物品上」的实测探测器。
 *
 * <h3>为什么用探测而不是查表</h3>
 * <p>
 * 直觉做法是按 {@code EnchantmentCategory} 的名字硬编码一张
 * 「ARMOR_FEET → 各种靴子」的对照表。但本模组有三个<b>自定义分类</b>
 * （{@code SHIELD} / {@code ARMS} / {@code PICKAXE}），它们由
 * {@code EnchantmentCategory.create(...)} 在运行期构造，判定逻辑写在
 * {@code CarianStyleEnchantments} 里的 lambda 中，查表方式无法覆盖，
 * 而且以后加新分类必然忘记同步这张表。
 * </p>
 * <p>
 * 因此改为拿一组代表性原版物品，逐个调用
 * {@link EnchantmentCategory#canEnchant(Item)} 实测。判定逻辑是什么，
 * 探测结果就是什么，不会有对不上的问题。
 * </p>
 *
 * <h3>性能</h3>
 * <p>
 * 每个附魔探测一次，共 113 × {@link #PROBE_ITEMS}.length 次调用，
 * 只在 {@link EnchantmentCodex} 首次构建索引时执行一次（客户端首次打开界面），
 * 结果固化进 {@link EnchantmentMeta}。不在任何 tick 或 render 路径上。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class ItemProbe {
    private static final Item[] PROBE_ITEMS = new Item[]{
            // 近战
            Items.DIAMOND_SWORD,
            Items.DIAMOND_AXE,
            Items.TRIDENT,
            // 远程
            Items.BOW,
            Items.CROSSBOW,
            // 工具
            Items.DIAMOND_PICKAXE,
            Items.DIAMOND_SHOVEL,
            Items.DIAMOND_HOE,
            Items.SHEARS,
            Items.FLINT_AND_STEEL,
            Items.FISHING_ROD,
            // 护甲
            Items.DIAMOND_HELMET,
            Items.DIAMOND_CHESTPLATE,
            Items.DIAMOND_LEGGINGS,
            Items.DIAMOND_BOOTS,
            Items.TURTLE_HELMET,
            // 特殊
            Items.SHIELD,
            Items.ELYTRA,
            Items.CARVED_PUMPKIN,
            Items.PLAYER_HEAD
    };
    private ItemProbe() {
    }
    @Nonnull public static List<Item> probe(@Nonnull AutoRegisterEnchantment annotation) {
        return java.util.List.of();
    }
    @Nonnull public static List<Item> probeByEnchantment(@Nonnull Enchantment enchantment) {
        return java.util.List.of();
    }
}

