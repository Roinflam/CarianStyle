package pers.roinflam.carianstyle.codex;

import net.minecraft.network.chat.Component;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 百科列表能显示的条目。
 *
 * <h3>为什么要抽这个接口</h3>
 * <p>
 * 百科现在有两份数据源：本模组的 113 个附魔（{@link EnchantmentMeta}），
 * 以及原版与其它模组的全部附魔（{@link ForeignMeta}）。
 * 两者能提供的信息差别很大——本模组的有主题分组、可调数值、宝藏归因，
 * 外部附魔这些一概没有。
 * </p>
 * <p>
 * 但<b>左栏列表需要的东西是一样的</b>：名字、稀有度、等级上限、冲突数、分组。
 * 把这几项抽成接口之后，{@code CodexList} 一份代码同时服务两个标签页，
 * 而各自的详情面板仍然可以按自己的信息量去写，互不迁就。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public interface CodexEntry {

    /**
     * 唯一键，也是跳转时使用的标识。
     * <p>
     * 本模组的附魔用裸 id（{@code gravitas}），外部附魔用完整的
     * {@code 命名空间:路径}（{@code minecraft:knockback}）。
     * 键里有没有冒号，正好也是界面判断「该跳到哪个标签页」的依据。
     * </p>
     *
     * @return 唯一键
     */
    @Nonnull
    String getKey();

    /**
     * @return 显示名
     */
    @Nonnull
    Component getDisplayName();

    /**
     * @return 常规最大等级
     */
    int getMaxLevel();

    /**
     * @return 稀有度显示名
     */
    @Nonnull
    Component getRarityName();

    /**
     * @return 稀有度颜色（ARGB），列表右侧圆点与详情页徽章共用
     */
    int getRarityColor();

    /**
     * @return 稀有度排序权重，越小越靠前
     */
    int getRarityOrder();

    /**
     * @return 冲突附魔总数（含本模组与外部）
     */
    int getConflictCount();

    /**
     * @return 分组键，用于判断相邻条目是否属于同一组
     */
    @Nonnull
    String getGroupKey();

    /**
     * @return 分组显示名（列表里的分隔标题）
     */
    @Nonnull
    Component getGroupName();

    /**
     * @return 分组强调色（ARGB）
     */
    int getGroupColor();

    /**
     * 是否匹配搜索关键字。
     *
     * @param lowerKeyword 已转小写的关键字；空串视为全部匹配
     * @return 是否匹配
     */
    boolean matches(@Nullable String lowerKeyword);
}
