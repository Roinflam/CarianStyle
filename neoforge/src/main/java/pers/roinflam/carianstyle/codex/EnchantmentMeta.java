package pers.roinflam.carianstyle.codex;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.pack.DisabledCreativeFilter;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
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
 * 对该附魔的类别（{@code EnchantTarget}）逐个试探得出的<b>实测结果</b>，
 * 而不是根据分类名硬编码的猜测。这样自定义分类（SHIELD / ARMS / PICKAXE）
 * 也能得到正确答案。
 * </p>
 *
 * <h3>两个「附魔」</h3>
 * <p>
 * 1.21.1 的附魔分成两半：本模组的行为对象 {@link EnchantmentBase}（注解、数值、回调），
 * 和世界注册表里的定义（{@code Holder<Enchantment>}：花费、权重、互斥、获取途径标签）。
 * 后者是服务器按它的配置生成、进世界时下发的，百科的获取方式、附魔台数据、冲突、「放入物品」判定都读它；
 * 所以本类只在一个世界里有效，换了世界由 {@link EnchantmentCodex} 重建。
 * </p>
 *
 * @author RoinFlam
 * @version 1.1
 */
public final class EnchantmentMeta implements CodexEntry {

    /** 附魔注册 id（等同注解的 {@code id()}，也是语言键的后缀） */
    private final String id;

    /** 附魔行为对象（注解、可调数值、衰减都按它算） */
    private final EnchantmentBase base;

    /** 当前世界注册表里的附魔定义（服务器下发） */
    private final Holder<Enchantment> holder;

    /** 附魔实现类 */
    private final Class<?> ownerClass;

    /** 模组内部实现分类（追忆 / 战技 / 律法 / 死亡 / 通用），仅在详情页作为信息展示 */
    private final CodexGroup group;

    /** 题材主题（群星 / 癫火 / 蒙格温王朝……），列表按它分组 */
    private final CodexTheme theme;

    /** 模组自定义稀有度 */
    private final EnchantmentRarity rarity;

    /** 常规最大等级（取自注册表里的定义） */
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
     * 取自注解的 {@code type()}（{@code EnchantTarget}）的小写名，和 1.20.1 的
     * 「{@code customType} 非空时用它，否则用原版 {@code type()}」得到的是同一组名字。
     * </p>
     */
    private final String categoryKey;

    /**
     * 冲突的本模组附魔 id。
     * <p>由 {@link EnchantmentCodex} 实测 {@code isCompatibleWith} 得出，
     * 不只是注解里声明的那些——详见该类的说明。</p>
     */
    private final List<String> conflictIds = new ArrayList<>();

    /** 冲突的原版（及其它模组）附魔，点了切到「其他附魔」标签页 */
    private final List<Holder<Enchantment>> foreignConflicts = new ArrayList<>();

    /**
     * 是否与自己所属实现分类的<b>全部</b>其它成员互斥。
     * <p>
     * {@code EnchantmentBase.checkCompatibility} 里有一条「同类别互斥（GENERAL 除外）」，
     * 于是 25 个战技之间两两互斥、14 个追忆之间两两互斥。
     * 若把它们逐个列出来，战技的冲突栏会排满 24 个徽章，读者反而抓不到重点。
     * 这个标记让界面能折叠成一句「与全部战技互斥」。
     * </p>
     */
    private boolean conflictsWholeGroup;

    /** 所属分类的成员总数（含自己），用于在界面上写出「共 N 个」 */
    private int groupMemberCount;

    /** 名称语言键 */
    private final String nameKey;

    /** 描述语言键 */
    private final String descKey;

    /** 预先转小写的搜索索引（名称 + 描述 + id），避免每次输入都重新 toLowerCase */
    private String searchIndex = "";

    /**
     * 由注解、行为对象与注册表里的定义构造元数据。
     *
     * @param base       附魔行为对象
     * @param holder     当前世界注册表里的附魔定义
     * @param annotation 该附魔类上的自动注册注解
     * @param group      所属自定义分组
     */
    EnchantmentMeta(@Nonnull EnchantmentBase base,
                    @Nonnull Holder<Enchantment> holder,
                    @Nonnull AutoRegisterEnchantment annotation,
                    @Nonnull CodexGroup group) {
        this.base = base;
        this.holder = holder;
        this.ownerClass = base.getClass();
        this.id = annotation.id();
        this.group = group;
        // 未登记主题的附魔归到「战技」这一最宽泛的组，保证它不会从列表里消失
        CodexTheme resolved = CodexTheme.resolve(this.id);
        this.theme = resolved != null ? resolved : CodexTheme.ASH_OF_WAR;
        this.rarity = annotation.rarity();
        this.maxLevel = holder.value().getMaxLevel();

        this.nameKey = "enchantment." + Reference.MOD_ID + "." + this.id;
        this.descKey = this.nameKey + ".desc";

        // 槽位：注解数组转不可变列表，避免调用方误改
        List<EquipmentSlot> slotList = new ArrayList<>(annotation.slots().length);
        Collections.addAll(slotList, annotation.slots());
        this.slots = Collections.unmodifiableList(slotList);

        // 适用物品：实测探测（构建期一次，之后复用）
        this.applicableItems = ItemProbe.probe(annotation);

        this.categoryKey = annotation.type().name().toLowerCase(Locale.ROOT);
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
     * @param other 冲突方（注册表里的定义）
     */
    void addForeignConflict(@Nonnull Holder<Enchantment> other) {
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
    public List<Holder<Enchantment>> getForeignConflicts() {
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

    /**
     * @return 当前世界注册表里的附魔定义；「放入物品」判定、冲突、获取方式都读它
     */
    @Override
    @Nonnull
    public Holder<Enchantment> getEnchantment() {
        return holder;
    }

    /**
     * @return 附魔行为对象（可调数值、等级衰减按它算）
     */
    @Nonnull
    public EnchantmentBase getBase() {
        return base;
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

    /**
     * @return 是否为宝藏附魔（只能通过战利品/交易获得）；读注册表标签，{@code /reload} 后跟着变
     */
    public boolean isTreasure() {
        return holder.is(EnchantmentTags.TREASURE);
    }

    /**
     * @return 是否为诅咒附魔
     */
    public boolean isCurse() {
        return holder.is(EnchantmentTags.CURSE);
    }

    /**
     * @return 这个附魔现在的获取途径（每次现读标签，不缓存）
     */
    @Nonnull
    public Acquisition getAcquisition() {
        return Acquisition.of(holder);
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
     * 百科「获取方式」一段的几个标记。
     *
     * <h3>为什么读标签</h3>
     * <p>
     * 1.20.1 这几项是客户端现场读本地配置算的（禁用、宝藏开关、交易开关），配置不同步，
     * 服务器改过而客户端没跟上时百科就说错。1.21.1 它们都是服务器生成的内置数据包里的标签，
     * 进世界、{@code /reload} 时下发到客户端，这里读到的就是这台服务器的真实情况；
     * 数据包作者往标签里加减的也一并算上（附魔台、交易、战利品本来就只看这几个标签）。
     * </p>
     * <p>
     * 对应关系：附魔台 = {@code #minecraft:in_enchanting_table}，战利品 = {@code #minecraft:on_random_loot}，
     * 交易 = {@code #minecraft:tradeable}，宝藏 = {@code #minecraft:treasure}，禁用 = {@code #carianstyle:disabled}。
     * </p>
     * <p>
     * 只依赖注册表 Holder、不碰客户端类，服务端也能算（测试台拿它和生成结果对照）。
     * </p>
     */
    public static final class Acquisition {
        /** 被配置禁用：其余途径全部不成立 */
        public final boolean disabled;
        /** 宝藏附魔：附魔台出不来 */
        public final boolean treasure;
        /** 附魔台能出 */
        public final boolean table;
        /** 战利品箱、钓鱼、怪物掉落的附魔书能出 */
        public final boolean loot;
        /** 图书管理员的交易能出 */
        public final boolean villager;

        private Acquisition(boolean disabled, boolean treasure, boolean table, boolean loot, boolean villager) {
            this.disabled = disabled;
            this.treasure = treasure;
            this.table = table;
            this.loot = loot;
            this.villager = villager;
        }

        /**
         * @param holder 世界注册表里的附魔定义
         * @return 它现在的获取途径
         */
        @Nonnull
        public static Acquisition of(@Nonnull Holder<Enchantment> holder) {
            return new Acquisition(
                    holder.is(DisabledCreativeFilter.DISABLED),
                    holder.is(EnchantmentTags.TREASURE),
                    holder.is(EnchantmentTags.IN_ENCHANTING_TABLE),
                    holder.is(EnchantmentTags.ON_RANDOM_LOOT),
                    holder.is(EnchantmentTags.TRADEABLE));
        }
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
        /** 追忆 */
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
