package pers.roinflam.carianstyle.codex;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.fml.ModList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
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
 * <h3>⚠ 为什么补充描述不写成 {@code enchantment.minecraft.*.desc}</h3>
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
 * @author FlameForge
 * @version 1.0
 */
public final class ForeignMeta implements CodexEntry {

    /** 附魔实例 */
    private final Enchantment enchantment;

    /** 注册名 */
    private final ResourceLocation registryName;

    /** {@code 命名空间:路径} 形式的唯一键 */
    private final String key;

    /** 所属模组的命名空间 */
    private final String namespace;

    /** 实测可附魔的代表性物品 */
    private final List<Item> applicableItems;

    /** 冲突的外部附魔键（{@code 命名空间:路径}） */
    private final List<String> foreignConflicts = new ArrayList<>();

    /** 冲突的本模组附魔 id（裸 id，点击可跳回附魔百科标签页） */
    private final List<String> carianConflicts = new ArrayList<>();

    /**
     * 描述语言键的候选，按优先级排列。
     * <p>生态里两种后缀都有人用，都查一遍成本可以忽略，覆盖率却明显不同。</p>
     */
    private final String[] descKeys;

    /** 预先转小写的搜索索引 */
    private String searchIndex = "";

    /**
     * 描述里的格式占位符：{@code %1$s} 这种带位置的，或裸 {@code %s}。
     * <p>用于算出该传几个参数，见 {@link #getDescription(int)}。</p>
     */
    private static final Pattern FORMAT_ARG = Pattern.compile("%(?:(\\d+)\\$)?s");

    ForeignMeta(@Nonnull Enchantment enchantment, @Nonnull ResourceLocation registryName) {
        this.enchantment = enchantment;
        this.registryName = registryName;
        this.key = registryName.toString();
        this.namespace = registryName.getNamespace();
        String base = enchantment.getDescriptionId();
        // 顺序即优先级：标准约定优先，本模组的补充永远排最后
        this.descKeys = "minecraft".equals(this.namespace)
                ? new String[]{base + ".desc", base + ".description",
                        "carianstyle.vanilla_desc." + registryName.getPath()}
                : new String[]{base + ".desc", base + ".description"};
        this.applicableItems = ItemProbe.probeByEnchantment(enchantment);
    }

    /**
     * 构建搜索索引。必须在语言表就绪后调用。
     */
    void buildSearchIndex() {
        this.searchIndex = (getDisplayName().getString() + '\u0000' + key)
                .toLowerCase(Locale.ROOT);
    }

    /**
     * 追加一条与外部附魔的冲突。
     *
     * @param otherKey 对方的唯一键
     */
    void addForeignConflict(@Nonnull String otherKey) {
        if (!otherKey.equals(key) && !foreignConflicts.contains(otherKey)) {
            foreignConflicts.add(otherKey);
        }
    }

    /**
     * 追加一条与本模组附魔的冲突。
     *
     * @param carianId 本模组附魔的裸 id
     */
    void addCarianConflict(@Nonnull String carianId) {
        if (!carianConflicts.contains(carianId)) {
            carianConflicts.add(carianId);
        }
    }

    /**
     * @return 附魔实例
     */
    @Nonnull
    public Enchantment getEnchantment() {
        return enchantment;
    }

    /**
     * @return 注册名
     */
    @Nonnull
    public ResourceLocation getRegistryName() {
        return registryName;
    }

    /**
     * @return 实测可附魔的代表性物品
     */
    @Nonnull
    public List<Item> getApplicableItems() {
        return applicableItems;
    }

    /**
     * @return 冲突的外部附魔键
     */
    @Nonnull
    public List<String> getForeignConflicts() {
        return Collections.unmodifiableList(foreignConflicts);
    }

    /**
     * @return 冲突的本模组附魔 id
     */
    @Nonnull
    public List<String> getCarianConflicts() {
        return Collections.unmodifiableList(carianConflicts);
    }

    /**
     * 取描述。
     *
     * @return 该附魔的描述；作者未提供时返回统一的说明文案
     */
    @Nonnull
    public Component getDescription() {
        return getDescription(getMaxLevel());
    }

    /**
     * 取描述，并把格式占位符按给定等级填上。
     *
     * <h3>为什么要填</h3>
     * <p>
     * 有些模组的描述写成 {@code 一次挖掘区块对齐的%1$sx%1$sx%1$s的方块}，
     * 占位符留给等级，由它自己在物品提示框里填。百科直接
     * {@code Component.translatable(key)} 不传参，Minecraft 格式化失败后会把
     * <b>原文连同 {@code %1$s} 一起吐出来</b>——玩家看到的就是一串乱码似的东西。
     * </p>
     * <p>
     * 所以这里数一下需要几个参数，把等级重复填进去。
     * </p>
     *
     * <h3>为什么敢假设参数就是等级</h3>
     * <p>
     * 严格说这是个假设：占位符可以是任何东西。但附魔描述里的可变量几乎只有等级一种，
     * 而且界面上有等级选择条——玩家自己在选等级，填进去的是几一目了然。
     * 万一某个模组填的是别的东西，显示出的数字会不对，但至少不再是
     * {@code %1$s} 这种明显坏掉的样子；而恒定不变的原文反而会让人以为是百科的 bug。
     * </p>
     *
     * @param level 要代入的等级
     * @return 描述；作者未提供时返回统一说明
     */
    @Nonnull
    public Component getDescription(int level) {
        String key = resolveDescKey();
        if (key == null) {
            return Component.translatable("carianstyle.codex.foreign.no_description");
        }
        int argCount = countFormatArgs(I18n.get(key));
        if (argCount <= 0) {
            return Component.translatable(key);
        }
        Object[] args = new Object[argCount];
        java.util.Arrays.fill(args, level);
        return Component.translatable(key, args);
    }

    /**
     * 数出一段文本需要几个格式参数。
     *
     * @param text 文本
     * @return 参数个数；没有占位符时为 0
     */
    private static int countFormatArgs(@Nonnull String text) {
        Matcher m = FORMAT_ARG.matcher(text);
        int highest = 0;
        int bare = 0;
        while (m.find()) {
            if (m.group(1) == null) {
                bare++;
            } else {
                // 带位置的占位符可以重复引用同一个参数，取最大序号才是真实的参数个数
                highest = Math.max(highest, Integer.parseInt(m.group(1)));
            }
        }
        return Math.max(highest, bare);
    }

    /**
     * @return 描述里是否含格式占位符（界面据此决定要不要显示等级选择条）
     */
    public boolean hasFormatArgs() {
        String key = resolveDescKey();
        return key != null && countFormatArgs(I18n.get(key)) > 0;
    }

    /**
     * 找出第一个真实存在的描述键。
     *
     * @return 语言键；都不存在时返回 null
     */
    @Nullable
    private String resolveDescKey() {
        for (String key : descKeys) {
            if (I18n.exists(key)) {
                return key;
            }
        }
        return null;
    }

    /**
     * @return 用于提示「描述该写在哪」的语言键，即首选的那个
     */
    @Nonnull
    public String getPreferredDescKey() {
        return descKeys[0];
    }

    /**
     * 当前显示的描述是否来自本模组的补充，而非附魔自带。
     * <p>界面据此加一行说明——把补充的文字当成人家自带的展示是不诚实的。</p>
     *
     * @return 是否为本模组补充
     */
    public boolean isDescriptionSupplied() {
        String key = resolveDescKey();
        return key != null && key.startsWith("carianstyle.vanilla_desc.");
    }

    /**
     * @return 该附魔是否提供了描述（界面据此决定用正文色还是灰色）
     */
    public boolean hasDescription() {
        return resolveDescKey() != null;
    }

    /**
     * @return 最低等级
     */
    public int getMinLevel() {
        return enchantment.getMinLevel();
    }

    /**
     * @return 附魔台抽取权重
     */
    public int getWeight() {
        return enchantment.getRarity().getWeight();
    }

    // ==================== CodexEntry ====================

    @Override
    @Nonnull
    public String getKey() {
        return key;
    }

    @Override
    @Nonnull
    public Component getDisplayName() {
        return Component.translatable(enchantment.getDescriptionId());
    }

    @Override
    public int getMaxLevel() {
        return enchantment.getMaxLevel();
    }

    @Override
    @Nonnull
    public Component getRarityName() {
        return Component.translatable("carianstyle.codex.vanilla_rarity."
                + enchantment.getRarity().name().toLowerCase(Locale.ROOT));
    }

    @Override
    public int getRarityColor() {
        switch (enchantment.getRarity()) {
            case VERY_RARE:
                return 0xFFE0A05A;
            case RARE:
                return 0xFFC0A4E0;
            case UNCOMMON:
                return 0xFF9FD0C8;
            case COMMON:
            default:
                return 0xFF9C927E;
        }
    }

    @Override
    public int getRarityOrder() {
        // 原版稀有度枚举的声明顺序恰好是由常见到稀有，但仍显式写死语义，
        // 免得将来枚举顺序变了界面排序跟着悄悄变
        switch (enchantment.getRarity()) {
            case COMMON:
                return 0;
            case UNCOMMON:
                return 1;
            case RARE:
                return 2;
            case VERY_RARE:
            default:
                return 3;
        }
    }

    @Override
    public int getConflictCount() {
        return foreignConflicts.size() + carianConflicts.size();
    }

    @Override
    @Nonnull
    public String getGroupKey() {
        return namespace;
    }

    @Override
    @Nonnull
    public Component getGroupName() {
        if ("minecraft".equals(namespace)) {
            return Component.translatable("carianstyle.codex.foreign.vanilla");
        }
        // 优先显示模组的正式名称，拿不到再退回命名空间——
        // 「Apotheosis」比「apotheosis」好读，但后者至少不会显示成空白
        return ModList.get().getModContainerById(namespace)
                .map(c -> Component.literal(c.getModInfo().getDisplayName()))
                .orElseGet(() -> Component.literal(namespace));
    }

    @Override
    public int getGroupColor() {
        if ("minecraft".equals(namespace)) {
            return 0xFFB0B6C8;
        }
        // 按命名空间散列出一个稳定的色相：同一个模组每次进来颜色一致，
        // 不同模组彼此区分。限制在中高亮度，保证在暗底上看得清
        int hash = namespace.hashCode();
        float hue = Math.floorMod(hash, 360) / 360f;
        return 0xFF000000 | hsvToRgb(hue, 0.35f, 0.85f);
    }

    @Override
    public boolean matches(@Nullable String lowerKeyword) {
        return lowerKeyword == null || lowerKeyword.isEmpty() || searchIndex.contains(lowerKeyword);
    }

    /**
     * HSV 转 RGB。
     *
     * @param h 色相 0~1
     * @param s 饱和度 0~1
     * @param v 明度 0~1
     * @return RGB（不含 alpha）
     */
    private static int hsvToRgb(float h, float s, float v) {
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s);
        float q = v * (1 - f * s);
        float t = v * (1 - (1 - f) * s);
        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /**
     * 判断某个物品能否附上本附魔。
     *
     * @param item 物品
     * @return 是否可附魔
     */
    public boolean canEnchant(@Nonnull Item item) {
        return enchantment.canEnchant(new ItemStack(item));
    }
}
