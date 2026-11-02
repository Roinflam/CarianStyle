package pers.roinflam.carianstyle.codex;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「这个附魔能附在什么物品上」的实测探测器。
 *
 * <h3>为什么用探测而不是查表</h3>
 * <p>
 * 直觉做法是按 {@code EnumEnchantmentType} 的名字硬编码一张
 * 「ARMOR_FEET → 各种靴子」的对照表。但本模组有三个<b>自定义分类</b>
 * （{@code SHIELD} / {@code ARMS} / {@code PICKAXE}），它们由
 * {@code EnumHelper.addEnchantmentType(...)} 在运行期构造，判定逻辑写在
 * {@code CarianStyleEnchantments} 里的 lambda 中，查表方式无法覆盖，
 * 而且以后加新分类必然忘记同步这张表。
 * </p>
 * <p>
 * 因此改为拿一组代表性原版物品，逐个调用
 * {@link EnumEnchantmentType#canEnchantItem(Item)} 实测。判定逻辑是什么，
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
public final class ItemProbe {

    /**
     * 探测样本：覆盖全部原版附魔分类的代表性物品。
     * <p>
     * 只放<b>代表</b>，不放同类的全部变体——界面上列「钻石剑」比列
     * 「木剑/石剑/铁剑/金剑/钻石剑/下界合金剑」有用得多，也不会把详情页撑爆。
     * </p>
     * <p>
     * 顺序即界面展示顺序：武器 → 远程 → 工具 → 护甲 → 特殊。
     * </p>
     * <p>
     * 1.12.2 版：没有三叉戟、弩、海龟壳；「雕刻南瓜」对应 1.12.2 能戴在头上的南瓜方块，
     * 「玩家头颅」对应各种头颅共用的 {@code Items.SKULL}。
     * </p>
     */
    private static final Item[] PROBE_ITEMS = new Item[]{
            // 近战
            Items.DIAMOND_SWORD,
            Items.DIAMOND_AXE,
            // 远程
            Items.BOW,
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
            // 特殊
            Items.SHIELD,
            Items.ELYTRA,
            Item.getItemFromBlock(Blocks.PUMPKIN),
            Items.SKULL
    };

    private ItemProbe() {
    }

    /**
     * 探测某个附魔的适用物品。
     *
     * @param annotation 附魔类上的注册注解
     * @return 可附魔的物品列表（不可修改）；分类无法解析时返回空列表
     */
    @Nonnull
    public static List<Item> probe(@Nonnull AutoRegisterEnchantment annotation) {
        EnumEnchantmentType category = resolveCategory(annotation);
        if (category == null) {
            return Collections.emptyList();
        }

        List<Item> result = new ArrayList<>(4);
        for (Item item : PROBE_ITEMS) {
            try {
                if (category.canEnchantItem(item)) {
                    result.add(item);
                }
            } catch (Exception e) {
                // 自定义分类的判定 lambda 由本模组自己提供，理论上不会抛异常；
                // 但第三方模组也可能改写物品或附魔类型的判定，这里兜底避免一个物品的问题
                // 导致整个百科索引构建失败
                LogUtil.warn("卡利亚式附魔 - 探测附魔 %s 对物品 %s 的适用性时出错：%s",
                        annotation.id(), item.getRegistryName(), e.getMessage());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 直接用附魔实例探测适用物品。
     *
     * <h3>为什么外部附魔要走另一条路</h3>
     * <p>
     * 上面的 {@link #probe(AutoRegisterEnchantment)} 是拿注解里声明的分类去测，
     * 那是本模组自己的写法。原版和别家模组没有这个注解，
     * 而且它们常常<b>覆写判定方法</b>加额外条件
     * （比如「锋利」放行斧头）。
     * </p>
     * <p>
     * 所以这里直接问附魔本人 {@code canApply(stack)}——
     * 它就是铁砧判断能不能合上去时用的那个方法（1.20.1 叫 {@code canEnchant}），结果一定与游戏内一致。
     * </p>
     *
     * @param enchantment 附魔实例
     * @return 可附魔的代表性物品列表（不可修改）
     */
    @Nonnull
    public static List<Item> probeByEnchantment(@Nonnull Enchantment enchantment) {
        List<Item> result = new ArrayList<>(4);
        for (Item item : PROBE_ITEMS) {
            try {
                if (enchantment.canApply(new ItemStack(item))) {
                    result.add(item);
                }
            } catch (Exception e) {
                // 第三方附魔的 canApply 是别人写的，出异常不该让整个索引构建失败
                LogUtil.warn("卡利亚式附魔 - 探测附魔 %s 对物品 %s 的适用性时出错：%s",
                        enchantment.getName(), item.getRegistryName(), e.getMessage());
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 解析注解声明的附魔分类。
     * <p>
     * 与 {@code EnchantmentRegistry.registerSingle} 的解析顺序保持一致：
     * {@code customType} 非空时优先使用自定义分类，否则使用原版 {@code type}。
     * </p>
     *
     * @param annotation 注册注解
     * @return 解析出的分类；customType 填了但无法识别时返回 null
     */
    @Nullable
    private static EnumEnchantmentType resolveCategory(@Nonnull AutoRegisterEnchantment annotation) {
        String customType = annotation.customType();
        if (customType != null && !customType.isEmpty()) {
            EnumEnchantmentType custom = CarianStyleEnchantments.getCustomEnchantmentCategory(customType);
            if (custom == null) {
                LogUtil.warn("卡利亚式附魔 - 附魔 %s 声明了无法识别的 customType：%s",
                        annotation.id(), customType);
            }
            return custom;
        }
        return annotation.type();
    }
}
