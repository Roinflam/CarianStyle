package pers.roinflam.carianstyle.codex;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 单个附魔的元数据快照（百科界面的数据单元）。
 * <p>
 * 本类在 {@link EnchantmentCodex} 构建索引时创建一次，之后<b>只读</b>。
 * 所有字段在构造期一次性算好，界面渲染时不做任何反射、不做任何字符串拼接，
 * 因为 GUI 的 render 方法是每帧调用的热路径。
 * </p>
 *
 * <h3>为什么不直接在界面里读注解</h3>
 * <p>
 * {@code getClass().getAnnotation(...)} 每次调用都要走 JVM 的注解解析，
 * 而百科列表在搜索框输入时会对全部 113 个附魔逐个做匹配。
 * 把注解结果固化成本类的普通字段，搜索时只比较已经预先转成小写的字符串。
 * </p>
 *
 * <h3>关于「适用物品」</h3>
 * <p>
 * {@link #applicableItems} 是由 {@link ItemProbe} 用一组代表性原版物品
 * 对该附魔的 {@code EnchantmentCategory} 逐个试探得出的<b>实测结果</b>，
 * 而不是根据分类名硬编码的猜测。这样自定义分类（SHIELD / ARMS / PICKAXE）
 * 也能得到正确答案。
 * </p>
 *
 * @author FlameForge
 * @version 1.0
 */
public final class EnchantmentMeta implements CodexEntry {

    /** 附魔注册 id（等同注解的 {@code id()}，也是语言键的后缀） */
    private final String id;

    /** 附魔实例 */
    private final Enchantment enchantment;

    /** 附魔实现类 */
    private final Class<?> ownerClass;

    /** 模组内部实现分类（回忆 / 战技 / 律法 / 死亡 / 通用），仅在详情页作为信息展示 */
    private final CodexGroup group;

    /** 题材主题（群星 / 癫火 / 蒙格温王朝……），列表按它分组 */
    private final CodexTheme theme;

    /** 模组自定义稀有度 */
    private final EnchantmentRarity rarity;

    /** 常规最大等级（取自 {@code enchantment.getMaxLevel()}，已含配置影响） */
    private final int maxLevel;

    /** 适用装备槽位 */
    private final List<EquipmentSlot> slots;

    /** 实测可附魔的代表性物品 */
    private final List<Item> applicableItems;

    /**
     * 适用物品种类的语言键后缀。
     * <p>
     * 只列代表性物品的图标信息量太低——「暗月」那一格只画得出一把钻石剑，
     * 玩家看不出它到底是「只能附在剑上」还是「剑弓弩都行」。
     * 种类名才是这个问题的直接答案，图标退为佐证。
     * </p>
     * <p>
     * 取自注解：{@code customType} 非空时用它，否则用原版 {@code type()} 的枚举名，
     * 与 {@code EnchantmentRegistry.registerSingle} 的解析顺序保持一致。
     * </p>
     */
    private final String categoryKey;

    /**
     * 冲突的本模组附魔 id。
     * <p>由 {@link EnchantmentCodex} 实测 {@code isCompatibleWith} 得出，
     * 不只是注解里声明的那些——详见该类的说明。</p>
     */
    private final List<String> conflictIds = new ArrayList<>();

    /** 冲突的原版（及其它模组）附魔，展示用，不可点击跳转 */
    private final List<Enchantment> foreignConflicts = new ArrayList<>();

    /**
     * 是否与自己所属实现分类的<b>全部</b>其它成员互斥。
     * <p>
     * {@code EnchantmentBase.checkCompatibility} 里有一条「同类别互斥（GENERAL 除外）」，
     * 于是 25 个战技之间两两互斥、14 个回忆之间两两互斥。
     * 若把它们逐个列出来，战技的冲突栏会排满 24 个徽章，读者反而抓不到重点。
     * 这个标记让界面能折叠成一句「与全部战技互斥」。
     * </p>
     */
    private boolean conflictsWholeGroup;

    /** 所属分类的成员总数（含自己），用于在界面上写出「共 N 个」 */
    private int groupMemberCount;

    /** 是否为宝藏附魔（只能通过战利品/交易获得） */
    private final boolean treasure;

    /** 是否为诅咒附魔 */
    private final boolean curse;

    /** 名称语言键 */
    private final String nameKey;

    /** 描述语言键 */
    private final String descKey;

    /** 预先转小写的搜索索引（名称 + 描述 + id），避免每次输入都重新 toLowerCase */
    private String searchIndex = "";

    /**
     * 由注解与附魔实例构造元数据。
     *
     * @param enchantment 已注册的附魔实例
     * @param annotation  该附魔类上的自动注册注解
     * @param group       所属自定义分组
     */
    EnchantmentMeta(@Nonnull Enchantment enchantment,
                    @Nonnull AutoRegisterEnchantment annotation,
                    @Nonnull CodexGroup group) {
        this.enchantment = enchantment;
        this.ownerClass = enchantment.getClass();
        this.id = annotation.id();
        this.group = group;
        // 未登记主题的附魔归到「战技」这一最宽泛的组，保证它不会从列表里消失
        CodexTheme resolved = CodexTheme.resolve(this.id);
        this.theme = resolved != null ? resolved : CodexTheme.ASH_OF_WAR;
        this.rarity = annotation.rarity();
        this.maxLevel = enchantment.getMaxLevel();
        this.treasure = annotation.forceTreasure() || enchantment.isTreasureOnly();
        this.curse = annotation.isCurse();

        this.nameKey = "enchantment." + Reference.MOD_ID + "." + this.id;
        this.descKey = this.nameKey + ".desc";

        // 槽位：注解数组转不可变列表，避免调用方误改
        List<EquipmentSlot> slotList = new ArrayList<>(annotation.slots().length);
        Collections.addAll(slotList, annotation.slots());
        this.slots = Collections.unmodifiableList(slotList);

        // 适用物品：实测探测（构建期一次，之后复用）
        this.applicableItems = ItemProbe.probe(annotation);

        String custom = annotation.customType();
        this.categoryKey = (custom != null && !custom.isEmpty())
                ? custom.toLowerCase(Locale.ROOT)
                : annotation.type().name().toLowerCase(Locale.ROOT);
    }

    /**
     * 构建搜索索引。
     * <p>
     * 必须在语言文件加载完成后调用（客户端 {@code ClientTickEvent} 首次打开界面时），
     * 因为 {@code Component.translatable(...).getString()} 在语言表就绪前会返回原始键。
     * </p>
     */
    void buildSearchIndex() {
        String name = Component.translatable(nameKey).getString();
        String desc = Component.translatable(descKey).getString();
        this.searchIndex = (name + '\u0000' + desc + '\u0000' + id).toLowerCase(Locale.ROOT);
    }

    /**
     * 追加一条冲突关系（去重）。
     *
     * @param otherId 冲突方的附魔 id
     */
    void addConflict(@Nonnull String otherId) {
        if (!otherId.equals(this.id) && !conflictIds.contains(otherId)) {
            conflictIds.add(otherId);
        }
    }

    /**
     * 追加一条与外部附魔（原版或其它模组）的冲突。
     *
     * @param other 冲突方
     */
    void addForeignConflict(@Nonnull Enchantment other) {
        if (!foreignConflicts.contains(other)) {
            foreignConflicts.add(other);
        }
    }

    /**
     * 标记整类互斥。
     *
     * @param whole       是否与同类全部其它成员互斥
     * @param memberCount 该分类的成员总数（含自己）
     */
    void markGroupConflict(boolean whole, int memberCount) {
        this.conflictsWholeGroup = whole;
        this.groupMemberCount = memberCount;
    }

    /**
     * @return 是否与同实现分类的全部其它成员互斥
     */
    public boolean conflictsWithWholeGroup() {
        return conflictsWholeGroup;
    }

    /**
     * @return 所属实现分类的成员总数（含自己）
     */
    public int getGroupMemberCount() {
        return groupMemberCount;
    }

    /**
     * @return 冲突的外部附魔（原版 / 其它模组），不可修改
     */
    @Nonnull
    public List<Enchantment> getForeignConflicts() {
        return Collections.unmodifiableList(foreignConflicts);
    }

    /**
     * 判断本附魔是否匹配搜索关键字。
     *
     * @param lowerKeyword 已转小写的关键字；空串视为全部匹配
     * @return 是否匹配
     */
    @Override
    public boolean matches(@Nullable String lowerKeyword) {
        if (lowerKeyword == null || lowerKeyword.isEmpty()) {
            return true;
        }
        return searchIndex.contains(lowerKeyword);
    }

    // ==================== 只读访问器 ====================

    @Nonnull
    public String getId() {
        return id;
    }

    // ==================== CodexEntry ====================

    @Override
    @Nonnull
    public String getKey() {
        // 本模组的附魔用裸 id，与配置文件和跳转标识保持一致
        return id;
    }

    @Override
    @Nonnull
    public Component getRarityName() {
        return Component.translatable("carianstyle.codex.rarity."
                + rarity.name().toLowerCase(Locale.ROOT));
    }

    @Override
    public int getRarityColor() {
        switch (rarity) {
            case VERY_RARE:
                return 0xFFE0A05A;
            case RARE:
                return 0xFFC0A4E0;
            case UNCOMMON:
            default:
                return 0xFF9C927E;
        }
    }

    @Override
    public int getRarityOrder() {
        switch (rarity) {
            case UNCOMMON:
                return 0;
            case RARE:
                return 1;
            case VERY_RARE:
            default:
                return 2;
        }
    }

    @Override
    public int getConflictCount() {
        return conflictIds.size() + foreignConflicts.size();
    }

    @Override
    @Nonnull
    public String getGroupKey() {
        return theme.getKey();
    }

    @Override
    @Nonnull
    public Component getGroupName() {
        return theme.getDisplayName();
    }

    @Override
    public int getGroupColor() {
        return theme.getAccentColor();
    }

    @Nonnull
    public Enchantment getEnchantment() {
        return enchantment;
    }

    @Nonnull
    public Class<?> getOwnerClass() {
        return ownerClass;
    }

    @Nonnull
    public CodexGroup getGroup() {
        return group;
    }

    /**
     * @return 题材主题
     */
    @Nonnull
    public CodexTheme getTheme() {
        return theme;
    }

    @Nonnull
    public EnchantmentRarity getRarity() {
        return rarity;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Nonnull
    public List<EquipmentSlot> getSlots() {
        return slots;
    }

    @Nonnull
    public List<Item> getApplicableItems() {
        return applicableItems;
    }

    /**
     * 取适用物品种类的显示名。
     *
     * @return 已翻译的种类名；未登记翻译时回退到原始键
     */
    @Nonnull
    public Component getCategoryName() {
        return Component.translatable("carianstyle.codex.category." + categoryKey);
    }

    /**
     * @return 种类语言键后缀，供界面判断是否有对应翻译
     */
    @Nonnull
    public String getCategoryKey() {
        return categoryKey;
    }

    /**
     * 取冲突附魔 id 列表（已包含反向补全的关系）。
     *
     * @return 不可修改的 id 列表
     */
    @Nonnull
    public List<String> getConflictIds() {
        return Collections.unmodifiableList(conflictIds);
    }

    public boolean isTreasure() {
        return treasure;
    }

    public boolean isCurse() {
        return curse;
    }

    /**
     * 取附魔显示名（跟随当前语言）。
     *
     * @return 已翻译的名称组件
     */
    @Nonnull
    @Override
    public Component getDisplayName() {
        return Component.translatable(nameKey);
    }

    /**
     * 取附魔描述（跟随当前语言）。
     *
     * @return 已翻译的描述组件
     */
    @Nonnull
    public Component getDescription() {
        return Component.translatable(descKey);
    }

    /**
     * 模组自定义分组。
     * <p>
     * 与 {@code CarianStyleEnchantments} 里的四个 Set 一一对应，
     * 不在任何一个 Set 里的归入 {@link #NORMAL}。
     * </p>
     */
    public enum CodexGroup {
        /** 通用附魔 */
        NORMAL("carianstyle.codex.group.normal", 0xFF7FB2E5),
        /** 战技 */
        COMBAT_SKILL("carianstyle.codex.group.combat_skill", 0xFFE5B35C),
        /** 回忆 */
        RECOLLECT("carianstyle.codex.group.recollect", 0xFFC08CE5),
        /** 律法 */
        LAW("carianstyle.codex.group.law", 0xFF7FE5A8),
        /** 死亡 */
        DEAD("carianstyle.codex.group.dead", 0xFFE57F7F);

        private final String langKey;
        private final int accentColor;

        CodexGroup(String langKey, int accentColor) {
            this.langKey = langKey;
            this.accentColor = accentColor;
        }

        /**
         * @return 该分组的显示名
         */
        @Nonnull
        public Component getDisplayName() {
            return Component.translatable(langKey);
        }

        /**
         * @return 该分组在界面上的强调色（ARGB）
         */
        public int getAccentColor() {
            return accentColor;
        }
    }
}
