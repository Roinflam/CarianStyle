package pers.roinflam.carianstyle.codex;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.fml.ModList;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PORT-STUB(G)
 *
 * 原版及其它模组附魔的元数据。
 *
 * <h3>能拿到什么，拿不到什么</h3>
 * <p>
 * 外部附魔没有本模组的 {@code AutoRegisterEnchantment} 注解，
 * 所以主题分组、可调数值、宝藏归因这些一概没有。但下面这些都能拿到，
 * 而且对玩家同样有用：
 * </p>
 * <ul>
 *   <li>名称、等级上限、稀有度与附魔台权重；</li>
 *   <li>宝藏 / 诅咒 / 可交易 / 可被发现 四个标志；</li>
 *   <li>适用物品——用 {@code canEnchant} 拿一组代表性物品实测；</li>
 *   <li><b>冲突关系</b>——同样用 {@code isCompatibleWith} 实测，
 *       既包括其它外部附魔，也包括本模组的附魔。</li>
 * </ul>
 *
 * <h3>描述从哪来</h3>
 * <p>
 * 原版附魔<b>本体没有描述</b>——Mojang 只给了名字。生态里的通行做法是按
 * {@code <名称键>.desc} 或 {@code <名称键>.description} 提供一条语言键，
 * Darkhax 的 Enchantment Descriptions 就是这么给原版补全的，
 * 本模组自己也是这么写的。所以这里两个后缀都查。
 * </p>
 * <p>
 * <b>这意味着装了那个模组之后，本页的原版附魔会自动有描述</b>，
 * 双方都不需要为此改一行代码——这正是遵守既有约定而不是自造一套的好处。
 * 反过来，本模组的 {@code enchantment.carianstyle.<id>.desc} 也会被它读到，
 * 在物品提示框里一并显示。
 * </p>
 * <p>
 * 两个都找不到、且是原版附魔时，回退到本模组自带的一份补充描述
 * （{@code carianstyle.vanilla_desc.<路径>}）——Mojang 从来没给附魔写过描述，
 * 百科不该因为玩家没装某个特定模组就整页空着。
 * </p>
 *
 * <h3>为什么补充描述不写成 {@code enchantment.minecraft.*.desc}</h3>
 * <p>
 * 那是<b>全局约定的键</b>。写进去之后，Enchantment Descriptions 之类按同一约定
 * 读取的模组会把本模组的文本显示在物品提示框里，按资源加载顺序覆盖掉人家写的版本——
 * 一个附魔百科没有理由去改别人物品提示框里的字。
 * </p>
 * <p>
 * 所以补充描述放在本模组自己的命名空间下，只有这个百科会读，对外零影响；
 * 而且优先级排在最后：只要任何一方按标准约定提供了描述，用的就是对方的。
 * </p>
 * <p>
 * 界面上也会标注这一条是补充的，不会冒充成附魔自带的说明。
 * </p>
 *
 * @author RoinFlam
 * @version 1.0
 */
@Deprecated
public final class ForeignMeta implements CodexEntry {
    private static final Pattern FORMAT_ARG = Pattern.compile("%(?:(\\d+)\\$)?s");
    ForeignMeta(@Nonnull Enchantment enchantment, @Nonnull ResourceLocation registryName) {
    }
    void buildSearchIndex() {
    }
    void addForeignConflict(@Nonnull String otherKey) {
    }
    void addCarianConflict(@Nonnull String carianId) {
    }
    @Nonnull public Enchantment getEnchantment() {
        return null;
    }
    @Nonnull public ResourceLocation getRegistryName() {
        return null;
    }
    @Nonnull public List<Item> getApplicableItems() {
        return java.util.List.of();
    }
    @Nonnull public List<String> getForeignConflicts() {
        return java.util.List.of();
    }
    @Nonnull public List<String> getCarianConflicts() {
        return java.util.List.of();
    }
    @Nonnull public Component getDescription() {
        return null;
    }
    @Nonnull public Component getDescription(int level) {
        return null;
    }
    public boolean hasFormatArgs() {
        return false;
    }
    @Nonnull public String getPreferredDescKey() {
        return "";
    }
    public boolean isDescriptionSupplied() {
        return false;
    }
    public boolean hasDescription() {
        return false;
    }
    public int getMinLevel() {
        return 0;
    }
    public int getWeight() {
        return 0;
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
    public boolean canEnchant(@Nonnull Item item) {
        return false;
    }
}

