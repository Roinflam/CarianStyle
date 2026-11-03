package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.registries.DeferredHolder;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.utils.util.EquipmentSlots;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

/**
 * 附魔基类
 * <p>
 * 提供完整的事件优先级控制和攻击者/受击者双视角支持
 * </p>
 * <p>
 * 注意：所有静态事件监听器已移至 EnchantmentEventHandler
 * </p>
 *
 * <h3>v3.0：1.21.1 起附魔是数据，本类只是行为对象</h3>
 * <p>
 * <b>问题：</b>1.21.1 的 {@link Enchantment} 变成了 final record（描述、定义、互斥集合、效果组件），
 * 附魔注册表是数据包注册表，加载世界时从 JSON 读，代码里既不能继承它写逻辑，也没有注册的口子。
 * </p>
 * <p>
 * <b>做法：</b>本类不再继承 {@code Enchantment}，只是一个纯行为对象：注解、id、注册表键，加上全部回调和工具方法。
 * 注册表里的条目由内置数据包按注解和配置生成（花费、权重、铁砧花费、宝藏、诅咒、获取途径、互斥）；
 * 行为对象和注册表条目之间按 {@link ResourceKey} 对应（{@code EnchantmentRegistry.byKey}）。
 * 原来覆写 {@code Enchantment} 的方法（{@code getRarity}、{@code getMinCost}、{@code isTreasureOnly}、
 * {@code isTradeable}、{@code isDiscoverable}、{@code checkCompatibility} 等）语义不变，改成给生成器读的普通方法。
 * </p>
 * <p>
 * <b>不覆写 {@code equals / hashCode}：</b>{@code EffectModifierHandler} 用 {@code HashSet} 收集行为对象，
 * 按身份哈希的迭代行为和 1.20.1 是同一类。
 * </p>
 *
 * <h3>历史修复（行为照旧，只是改由内置数据包体现）</h3>
 * <ul>
 *   <li>v2.2：{@link #isDisabled()} 读 {@code uninstallEnchantment} 黑名单；被禁用的附魔不进附魔台、战利品、交易，
 *       三个 dispatch 方法也不触发。1.21.1 里获取途径由标签决定，禁用的附魔仍然注册（存档里带它的物品不丢附魔），
 *       只是不进任何获取标签。</li>
 *   <li>v2.3：附魔台权重按稀有度映射回原版（UNCOMMON 5、RARE 2、VERY_RARE 1），铁砧花费倍率随之是 2 / 4 / 8；
 *       模组附魔默认不进图书管理员的附魔书交易池（1.20.1 交易池等概率抽，一百多个模组附魔会把经验修补等原版附魔
 *       的概率稀释到四分之一左右）。</li>
 *   <li>v2.4：两项改由 {@code useVanillaRarityWeight}（关掉时权重 10、铁砧 1）和 {@code allowVillagerBookTrade} 控制。
 *       1.21.1 这两项写进附魔 JSON / 标签，改了要重进世界（权重、铁砧）或 {@code /reload}（交易）才生效。</li>
 * </ul>
 *
 * @author RoinFlam
 * @version 3.0
 */
public abstract class EnchantmentBase {

    protected final EnchantmentRarity enchantmentRarity;

    @Nullable
    protected final AutoRegisterEnchantment annotation;

    /**
     * 构造器传进来的类别和槽位：1.20.1 真正生效的就是这两个（传给 {@code Enchantment} 的 super），注解里那份只是记录。
     */
    private final EnchantTarget target;
    private final EquipmentSlot[] slots;

    /** 按 {@link EquipmentSlot#ordinal()} 记哪些槽位上的物品算数，见 {@link #worksIn} */
    private final boolean[] activeSlots;

    /** 注册表键 {@code carianstyle:<id>}；没有注解的类为 null */
    @Nullable
    private final ResourceKey<Enchantment> key;

    /**
     * 只当查表键用的 Holder，见 {@link #lookupHolder()}。
     */
    @Nullable
    private final DeferredHolder<Enchantment, Enchantment> lookupHolder;

    /**
     * 缓存禁用状态，避免每次调用都遍历数组
     * <p>
     * 最低位是结果（1 = 禁用），其余位是算出它时的配置版本号 {@link ConfigLoader#bakeGeneration()}。
     * 0 = 未检查过；版本号对不上就当作没缓存、重新计算。
     * 结果和版本号压在同一个 int 里，一次读写就是一个整体：配置重载在文件监视线程上执行，
     * 拆成两个字段的话，服务端线程可能读到「新版本号 + 旧结果」。
     * 配置重载后也可以通过 invalidateDisabledCache() 直接清除。
     * </p>
     */
    private int disabledCacheStamp = 0;

    /**
     * 构造器（注解注册方式）
     *
     * @param target 附魔能上哪类物品（和注解的 {@code type} 一致）
     * @param slots  适用装备槽位（和注解的 {@code slots} 一致）
     */
    protected EnchantmentBase(@Nonnull EnchantTarget target, @Nonnull EquipmentSlot[] slots) {
        this.target = target;
        this.slots = slots.clone();
        this.activeSlots = activeSlotsOf(this.slots);
        this.annotation =this.getClass().getAnnotation(AutoRegisterEnchantment.class);

        if (annotation != null) {
            this.enchantmentRarity = annotation.rarity();
            this.key = ResourceKey.create(Registries.ENCHANTMENT,
                    ResourceLocation.fromNamespaceAndPath(Reference.MOD_ID, annotation.id()));
            this.lookupHolder = DeferredHolder.create(this.key);
            CarianStyleEnchantments.ENCHANTMENTS.add(this);
            LogUtil.debug("卡利亚式附魔 - 通过注解注册附魔: %s", annotation.id());
        } else {
            this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
            this.key = null;
            this.lookupHolder = null;
            LogUtil.warn("卡利亚式附魔 - 附魔类 %s 未使用AutoRegisterEnchantment注解",
                    this.getClass().getSimpleName());
        }
    }

    // ==================== 身份 ====================

    /**
     * 附魔 id（注解里的 {@code id}，不带命名空间）；没有注解时为 null
     */
    @Nullable
    public String getId() {
        return annotation != null ? annotation.id() : null;
    }

    /**
     * 注册表键 {@code carianstyle:<id>}。往物品上写附魔、取名字、判标签时用它到世界的注册表里取真 Holder：
     * {@code level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(getKey())}。
     * 真 Holder 不跨世界缓存（单人游戏的客户端和内置服务器也各有一份）。
     */
    public ResourceKey<Enchantment> getKey() {
        return key;
    }

    /**
     * 只当查表键用的 Holder，包内私有，只给 {@link EnchantmentLevels#get} 用。
     * <p>
     * 它是按键建的 {@code DeferredHolder}，没有绑定值：{@code value()} 会抛异常，{@code is(TagKey)} 恒 false，
     * 拿它写进物品再编码会失败。物品组件按 Holder 的 {@code equals / hashCode}（按键）查表，所以只拿来查等级是对的。
     * 判标签、写物品、取定义一律用世界注册表里的真 Holder（{@link #getKey()}）。
     * </p>
     */
    @Nullable
    Holder<Enchantment> lookupHolder() {
        return lookupHolder;
    }

    @Nullable
    public AutoRegisterEnchantment getAnnotation() {
        return annotation;
    }

    @Nonnull
    public EnchantmentRarity getEnchantmentRarity() {
        return enchantmentRarity;
    }

    /**
     * 附魔能上哪类物品（构造器给的，1.20.1 真正生效的类别）
     */
    @Nonnull
    public EnchantTarget getTarget() {
        return target;
    }

    /**
     * 适用装备槽位（构造器给的）
     */
    @Nonnull
    public EquipmentSlot[] getSlots() {
        return slots.clone();
    }

    /**
     * 放在这个槽位上的物品，它身上的本附魔算不算数。
     * <p>
     * <b>问题</b>：扫装备的几处（玩家装备缓存、伤害 / 死亡 / 治疗 / 暴击扫描、光环显示）原来 6 个槽位全扫，不看附魔适用的槽位。
     * 胸甲附魔是按「每件带这个附魔的物品」各结算一次的，身上穿一件、两只手各拿一件，效果就叠三次。
     * </p>
     * <p>
     * <b>为什么这么改</b>：只认附魔声明的槽位。主手、副手当同一类：1.12.2 起武器附魔就把副手算进去（双持），这一点不变；
     * 主副手都有同一个附魔时只结算等级高的那把，见 {@code EnchantmentEventHandler#shadowedByOtherHand}。
     * </p>
     * <p>
     * <b>行为影响</b>：护甲附魔的装备拿在手上不再生效（包括黑焰仪式、灾祸原来额外数的主手）；武器附魔放进护甲槽
     * （只有指令放得进去）也不生效。正常穿戴、手持不受影响。
     * </p>
     *
     * @param slot 物品所在槽位
     * @return 算数为 true
     */
    public boolean worksIn(@Nonnull EquipmentSlot slot) {
        int i = slot.ordinal();
        return i < activeSlots.length && activeSlots[i];
    }

    private static boolean[] activeSlotsOf(@Nonnull EquipmentSlot[] slots) {
        boolean[] active = new boolean[EquipmentSlot.values().length];
        for (EquipmentSlot slot : slots) {
            if (slot.getType() == EquipmentSlot.Type.HAND) {
                active[EquipmentSlot.MAINHAND.ordinal()] = true;
                active[EquipmentSlot.OFFHAND.ordinal()] = true;
            } else {
                active[slot.ordinal()] = true;
            }
        }
        return active;
    }

    /**
     * 翻译键 {@code enchantment.carianstyle.<id>}，和 1.20.1 {@code Enchantment#getDescriptionId} 一样
     */
    @Nonnull
    public String getDescriptionId() {
        return "enchantment." + Reference.MOD_ID + "." + (annotation != null ? annotation.id() : "unknown");
    }

    // ==================== 稀有度、权重、花费（内置数据包读） ====================

    /**
     * 附魔台权重，对应 1.20.1 覆写的 {@code getRarity().getWeight()}。
     * <p>
     * {@code useVanillaRarityWeight} 开（默认）时按模组稀有度映射回原版权重：UNCOMMON 5、RARE 2、VERY_RARE 1；
     * 关掉时退回 1.20.1 构造器里传给 super 的占位稀有度 COMMON，权重 10（详见类注释 v2.3 / v2.4）。
     * </p>
     */
    public int getWeight() {
        if (!ConfigLoader.useVanillaRarityWeight) {
            return 10;
        }
        return switch (enchantmentRarity) {
            case VERY_RARE -> 1;
            case RARE -> 2;
            case UNCOMMON -> 5;
        };
    }

    /**
     * 铁砧每级花费，对应 1.20.1 铁砧按 {@code getRarity()} 取的倍率：COMMON 1、UNCOMMON 2、RARE 4、VERY_RARE 8。
     * <p>
     * 1.21.1 铁砧每级加 {@code anvil_cost}、附魔书减半至少 1，和 1.20.1 的算式一致。
     * </p>
     */
    public int getAnvilCost() {
        if (!ConfigLoader.useVanillaRarityWeight) {
            return 1;
        }
        return switch (enchantmentRarity) {
            case VERY_RARE -> 8;
            case RARE -> 4;
            case UNCOMMON -> 2;
        };
    }

    public int getMaxLevel() {
        return enchantmentRarity.getMaxLevel();
    }

    /**
     * 1 级的最低花费 B（未乘附魔难度）
     */
    public int getBaseEnchantability() {
        if (annotation != null && annotation.baseEnchantability() != -1) {
            return annotation.baseEnchantability();
        }
        return enchantmentRarity.getBaseEnchantability();
    }

    /**
     * 每级花费增量 M（未乘附魔难度），固定花费为 0
     */
    public int getLevelMultiplier() {
        if (annotation != null && annotation.baseEnchantability() != -1) {
            return annotation.levelMultiplier();
        }
        return enchantmentRarity == EnchantmentRarity.VERY_RARE ? 0 : enchantmentRarity.getLevelMultiplier();
    }

    /**
     * 附魔 JSON 里 {@code min_cost.base}：{@code floor(B × 难度)}。
     * <p>
     * 1.20.1 是整体 {@code (int) ((B + (等级-1) × M) × 难度)}，1.21.1 的花费只能是线性的
     * {@code base + per × (等级-1)}；难度 1.0（默认）和 2.0（整合包）时两者逐级相等，其它难度个别等级会差 1～2。
     * </p>
     */
    public int getCostBase(double difficulty) {
        return (int) Math.floor(getBaseEnchantability() * difficulty);
    }

    /**
     * 附魔 JSON 里 {@code min_cost.per_level_above_first}：{@code round(M × 难度)}；{@code max_cost} 的 per 相同
     */
    public int getCostPerLevel(double difficulty) {
        return (int) Math.round(getLevelMultiplier() * difficulty);
    }

    /**
     * 按当前配置、1.21.1 的线性花费算这一级的最低附魔能力（图鉴显示用；附魔台实际读的是注册表里的 JSON）
     */
    public int getMinCost(int enchantmentLevel) {
        double d = ConfigLoader.enchantingDifficulty;
        return getCostBase(d) + getCostPerLevel(d) * (enchantmentLevel - 1);
    }

    /**
     * 最高附魔能力 = 最低 + 50（1.20.1 的 113 个覆写全是这样）
     */
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    // ==================== 禁用检查（v2.2新增） ====================

    /**
     * 检查此附魔是否被配置黑名单禁用
     * <p>
     * 读取 ConfigLoader.uninstallEnchantment 数组，匹配注解中的 id
     * （匹配规则见 {@link ConfigLoader#isEnchantmentUninstalled(String)}，容忍命名空间、大小写等写法差异）。
     * </p>
     * <p>
     * 结果按配置版本号缓存：
     * <ul>
     *   <li>配置还没加载（版本号为 0）时不缓存。以前这时算出的「未禁用」会一直留在缓存里，
     *       要等 bake() 末尾的 invalidateAllDisabledCaches() 才清掉；</li>
     *   <li>bake() 先换数组、再递增版本号。文件监视线程重载配置的同时服务端线程正在计算的话，
     *       用旧数组算出的结果带的是旧版本号，下次调用发现对不上就会重算，不会把旧结果一直留着。</li>
     * </ul>
     * </p>
     *
     * @return true 表示此附魔已被禁用，不应产生任何效果
     */
    public boolean isDisabled() {
        // 无注解的附魔无法被禁用（没有id可匹配）
        if (annotation == null) {
            return false;
        }

        // 先读版本号再读数组：读到新版本号时一定能看到新数组
        int generation = ConfigLoader.bakeGeneration();
        int stamp = disabledCacheStamp;
        if (generation != 0 && (stamp >>> 1) == generation) {
            return (stamp & 1) != 0;
        }

        boolean disabled = ConfigLoader.isEnchantmentUninstalled(annotation.id());
        if (generation != 0) {
            disabledCacheStamp = (generation << 1) | (disabled ? 1 : 0);
        }
        return disabled;
    }

    /**
     * 清除禁用状态缓存（配置重载时调用）
     */
    public void invalidateDisabledCache() {
        disabledCacheStamp = 0;
    }

    /**
     * 静态工具方法：检查附魔是否被禁用
     * <p>
     * 供独立 @SubscribeEvent 方法使用，例如：
     * <pre>
     * EnchantmentBase ench = EnchantmentRegistry.getEnchantmentByClass(MyEnchantment.class);
     * if (EnchantmentBase.isEnchantmentDisabled(ench)) return;
     * </pre>
     * </p>
     *
     * @param enchantment 要检查的附魔
     * @return true 表示被禁用或为 null
     */
    public static boolean isEnchantmentDisabled(@Nullable EnchantmentBase enchantment) {
        if (enchantment == null) {
            return true;
        }
        return enchantment.isDisabled();
    }

    /**
     * 清除所有已注册附魔的禁用缓存
     * <p>
     * 在 ConfigLoader.bake() 中调用，确保配置热重载后黑名单立即生效
     * </p>
     */
    public static void invalidateAllDisabledCaches() {
        for (EnchantmentBase enchantment : CarianStyleEnchantments.ENCHANTMENTS) {
            enchantment.invalidateDisabledCache();
        }
    }

    // ==================== 获取途径（内置数据包按这些生成标签） ====================

    /**
     * 能不能上书（1.20.1 {@code isAllowedOnBooks}）：被禁用的不能
     */
    public boolean isAllowedOnBooks() {
        return !isDisabled();
    }

    /**
     * 模组附魔默认不进入村民（图书管理员）的附魔书交易池，对应 {@code minecraft:tradeable} 标签。
     * <p>
     * 1.20.1 的 {@code VillagerTrades.EnchantBookForEmeralds} 把所有可交易附魔收集成一个 List 后<b>等概率</b>抽取，
     * 完全不看稀有度权重。本模组一百多个附魔若全部可交易，会把原版约 40 个可交易附魔的池子撑到 150+，
     * 导致「经验修补」等原版关键附魔的出现概率降到原来的四分之一左右。1.21.1 的交易仍然按标签成员等概率抽。
     * </p>
     * <p>
     * 由 {@code ConfigLoader.allowVillagerBookTrade} 控制，默认 false（拦截）；被禁用的一律不能交易。
     * </p>
     *
     * @return 是否可被村民交易
     */
    public boolean isTradeable() {
        if (isDisabled()) {
            return false;
        }
        return ConfigLoader.allowVillagerBookTrade;
    }

    /**
     * 能不能从战利品、附魔台等随机途径得到（1.20.1 {@code isDiscoverable}），对应 {@code minecraft:on_random_loot}：被禁用的不能
     */
    public boolean isDiscoverable() {
        return !isDisabled();
    }

    /**
     * 是否宝藏附魔（不进附魔台、交易价格翻倍），对应 {@code minecraft:treasure} 标签
     */
    public boolean isTreasureOnly() {
        if (annotation != null && annotation.forceTreasure()) {
            return true;
        }

        return switch (enchantmentRarity) {
            case VERY_RARE -> ConfigLoader.isTreasureVeryRaryEnchantment;
            case RARE -> ConfigLoader.isTreasureRaryEnchantment;
            case UNCOMMON -> ConfigLoader.isTreasureUncommonEnchantment;
        };
    }

    /**
     * 是否诅咒（红色名字、砂轮保留），对应 {@code minecraft:curse} 标签
     */
    public boolean isCurse() {
        return annotation != null && annotation.isCurse();
    }

    // ==================== 互斥 ====================

    /**
     * 单边判断：本附魔能不能和 {@code other} 共存（1.20.1 {@code Enchantment#checkCompatibility} 的覆写点）。
     * <p>
     * 规则依次是：自己和自己不共存（1.20.1 原版基类的 {@code this != other}）；{@code allowWith} 里的类一定兼容；
     * 同一个非 GENERAL 分类（战技 / 追忆 / 律法 / 死亡）互斥；{@code conflictsWith} 里的类互斥。
     * 子类覆写时照 1.20.1 先判自己的规则、再 {@code super.checkCompatibility(other)}。
     * </p>
     * <p>
     * 1.21.1 运行期不调它：内置数据包生成时逐对求值，结果写进 {@code exclusive_set}。只要一边判了互斥就互斥
     * （1.21.1 的 {@code Enchantment.areCompatible} 两边的互斥集合都看），和 1.20.1 两边都调的结果一样。
     * </p>
     */
    protected boolean checkCompatibility(@Nonnull EnchantKey other) {
        if (other.base() == this) {
            return false;
        }

        if (annotation == null) {
            return true;
        }

        // 检查白名单：allowWith中的附魔始终兼容
        for (Class<?> allowedClass : annotation.allowWith()) {
            if (other.isOf(allowedClass)) {
                return true;
            }
        }

        // 同类别互斥（GENERAL类别除外）
        EnchantmentBase otherBase = other.base();
        AutoRegisterEnchantment otherAnnotation = otherBase != null ? otherBase.annotation : null;
        if (otherAnnotation != null) {
            if (annotation.category() == otherAnnotation.category() &&
                    annotation.category() != pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL) {
                return false;
            }
        }

        // 检查黑名单：conflictsWith中的附魔互斥
        for (Class<?> conflictClass : annotation.conflictsWith()) {
            if (other.isOf(conflictClass)) {
                return false;
            }
        }

        return true;
    }

    /**
     * {@link #checkCompatibility} 的公开入口，给内置数据包的生成器逐对求 {@code exclusive_set} 用。
     *
     * @param other 另一个附魔
     * @return true 表示本附魔这一边不排斥它
     */
    public final boolean compatibleWith(@Nonnull EnchantKey other) {
        return checkCompatibility(other);
    }

    // ==================== 名称后缀 ====================

    /**
     * 开了高等级衰减（且 showInName 没关）、并且这一级会被打折时，在名字后面补上实际生效等级，例如「锋利 L（生效 25 级）」。
     * 只按这一件物品上的等级算；护甲类附魔是几件加起来再衰减，穿齐一套时实际值以百科的说明为准。
     * <p>
     * 1.20.1 是覆写 {@code getFullname(int)}；1.21.1 名字由静态的 {@code Enchantment.getFullname(Holder, int)} 拼，
     * 由那边的 mixin 拿原版拼好的名字调这里补后缀。
     * </p>
     *
     * @param name  原版拼好的名字（含等级罗马数字）
     * @param level 物品上的等级
     * @return 需要时补了后缀的名字
     */
    @Nonnull
    public Component appendEffectiveLevel(@Nonnull Component name, int level) {
        if (!LevelDecay.showInName()) {
            return name;
        }
        int effective = LevelDecay.apply(this, level);
        if (effective == level) {
            return name;
        }
        return name.copy().append(Component.translatable("carianstyle.level_decay.effective", effective)
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    // ==================== 等级读取 ====================

    /**
     * 从武器（主手+副手）获取附魔等级
     *
     * @param entity 实体
     * @return 附魔等级（取最大值，已应用等级上限）
     */
    protected int getEnchantmentLevelFromWeapon(@Nonnull LivingEntity entity) {
        ItemStack mainHand = entity.getItemInHand(InteractionHand.MAIN_HAND);
        ItemStack offHand = entity.getItemInHand(InteractionHand.OFF_HAND);

        int level = 0;

        if (!mainHand.isEmpty()) {
            level = Math.max(level, EnchantmentLevels.get(this, mainHand));
        }

        if (!offHand.isEmpty()) {
            level = Math.max(level, EnchantmentLevels.get(this, offHand));
        }

        return applyLevelLimit(level);
    }

    /**
     * 从护甲获取附魔等级总和
     *
     * @param entity 实体
     * @return 附魔等级总和（已应用等级上限）
     */
    protected int getEnchantmentLevelFromArmor(@Nonnull LivingEntity entity) {
        int totalLevel = 0;

        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentLevels.get(this, armor);
            }
        }

        return applyLevelLimit(totalLevel);
    }

    /**
     * 应用高等级衰减与附魔等级上限
     *
     * @param level 原始等级
     * @return 限制后的等级
     */
    public int applyLevelLimit(int level) {
        return applyLevelLimit(level, 10);
    }

    /**
     * 应用高等级衰减（{@link LevelDecay}），再在 {@code levelLimit} 开启时钳到附魔自己的上限。
     * <p>
     * 先衰减再钳：钳制是对「实际生效等级」的硬上限，和衰减叠用时结果只会更低，不会因为先钳后衰减把 10 级再打一次折。
     * </p>
     *
     * @param level 原始等级（护甲类是几件加起来的总和）
     * @param cap   {@code levelLimit} 开启时的上限
     * @return 实际生效等级
     */
    public int applyLevelLimit(int level, int cap) {
        return applyLevelLimit(this, level, cap);
    }

    /**
     * {@link #applyLevelLimit(int, int)} 的静态版本，给静态事件处理器用。
     */
    public static int applyLevelLimit(@Nullable EnchantmentBase enchantment, int level, int cap) {
        level = LevelDecay.apply(enchantment, level);
        if (ConfigLoader.levelLimit) {
            return Math.min(level, cap);
        }
        return level;
    }

    /**
     * 持有者全身这个附魔的最高等级（已应用衰减和等级上限）。
     * <p>
     * 给分发器按件回调、而效果只结算一次的附魔用：第一件触发后就进冷却，
     * 回调里的 level 只是扫到的第一件（靴子优先）的等级，不一定是最高的。
     * </p>
     *
     * @param holder 持有者
     * @return 最高等级；没有则为 0
     */
    protected int highestLevel(@Nonnull LivingEntity holder) {
        int highest = 0;
        for (EquipmentSlot slot : EquipmentSlots.SCAN) {
            highest = Math.max(highest,
                    applyLevelLimit(EnchantmentLevels.get(this, holder.getItemBySlot(slot))));
        }
        return highest;
    }

    /**
     * 只在 {@code levelLimit} 开启时钳到上限，不做衰减。
     * <p>
     * 给 {@code onXxx(ctx, level)} 回调里用：分发器传进来的 level 已经走过 {@link #applyLevelLimit(int)}，
     * 衰减过一次，再衰减就打了两次折。
     * </p>
     */
    public static int capLevel(int level, int cap) {
        if (ConfigLoader.levelLimit) {
            return Math.min(level, cap);
        }
        return level;
    }

    /**
     * 只做高等级衰减，不受 {@code levelLimit} 影响。给原本就不吃等级上限的附魔用。
     */
    public static int decayLevel(@Nullable EnchantmentBase enchantment, int level) {
        return LevelDecay.apply(enchantment, level);
    }

    // ==================== 蓄力与波及判断 ====================

    /**
     * 判断玩家是否刚完成满蓄力攻击
     * <p>
     * 委托给 {@link #isFullyCharged(LivingEntity)}。原来要求蓄力恰好等于 1，
     * 网络抖动时客户端蓄力条已满、服务端可能只有 0.95，满蓄力也会被判成没满；
     * 现在与其它蓄力判断统一为 &gt; 0.9。
     * </p>
     *
     * @param player 玩家
     * @return 是否满蓄力
     */
    protected boolean isJustSwung(@Nonnull Player player) {
        return isFullyCharged(player);
    }

    /**
     * 判断持有者这一下是否满蓄力（附魔的统一蓄力判断入口）
     * <p>
     * 不是玩家（怪物等没有攻击冷却）一律返回 true；玩家以
     * {@code getAttackStrengthScale(0.5F) > 0.9F} 为准，与原版暴击、横扫判断满蓄力的阈值一致。
     * </p>
     * <p>
     * NeoForge 21.1 的 {@code Player#attack} 和 Forge 1.20.1 一样在方法最末尾才清零攻击冷却，
     * 同一下近战挥击的整条事件链（AttackEntityEvent、CriticalHitEvent、攻击 / 受伤 / 伤害阶段、死亡）
     * 里读到的都是出手前的蓄力值，在这些事件里直接调用即可。
     * 附魔需要「消耗」蓄力时不要在事件里直接清零（会让同一下后面的判断全部失效），
     * 改用 {@link pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper#resetNextTick(Player)}
     * （需要「一下挥击只结算一次」时用 {@link pers.roinflam.carianstyle.utils.helper.AttackCooldownHelper#spendSwing(Player)}）。
     * </p>
     *
     * @param holder 附魔持有者，可为 null
     * @return 是否满蓄力
     */
    public static boolean isFullyCharged(@Nullable LivingEntity holder) {
        if (!(holder instanceof Player player)) {
            return true;
        }
        return player.getAttackStrengthScale(0.5F) > 0.9F;
    }

    /**
     * 范围伤害 / 范围减益要不要放过这个实体：自己、同类、队友、自己养的宠物。
     * <p>
     * 「同类」指和持有者同一种实体：玩家持有时不波及别的玩家，怪物持有时不波及同种怪物（僵尸不烧僵尸）。
     * 队友按原版 {@code isAlliedTo}（同一记分板队伍）判断；宠物 / 坐骑（狼、猫、鹦鹉、马……凡是有主人的实体）只放过持有者自己的。
     * 被直接命中的目标不走这里（它是这一下的受击者，该吃什么由各附魔自己决定）。
     * </p>
     *
     * @param attacker 附魔持有者，可为 null（null 时谁也不放过，返回 false）
     * @param other    候选的波及对象
     * @return true 表示应当放过它（不波及）
     */
    public static boolean isSameKindOrAlly(@Nullable LivingEntity attacker, @Nonnull LivingEntity other) {
        if (attacker == null) {
            return false;
        }
        if (other == attacker || other.getType() == attacker.getType()) {
            return true;
        }
        if (attacker.isAlliedTo(other)) {
            return true;
        }
        return other instanceof OwnableEntity pet && attacker.getUUID().equals(pet.getOwnerUUID());
    }

    // ==================== 攻击阶段（LivingAttackEvent）模板方法 (10个) ====================

    protected void onAttackHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttack(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefend(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDefendLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== 受伤阶段（LivingHurtEvent）模板方法 (10个) ====================

    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictim(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHurtAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== 伤害阶段（LivingDamageEvent）模板方法 (10个) ====================

    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimHighest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimHigh(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictim(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== 其他事件模板方法 ====================

    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
    }

    protected void onCriticalHit(@Nonnull EnchantmentContext ctx, int level) {
    }

    // ==================== 核心事件处理方法 ====================
    // handleLivingAttack/Hurt/Damage 保留向后兼容，但新的 EventHandler 不再调用它们
    // 新的 EventHandler 在 HIGHEST 时一次性扫描缓存，然后直接调用 dispatch 方法

    /**
     * 处理攻击阶段（向后兼容，新EventHandler不使用此方法）
     *
     * @param event    事件
     * @param priority 优先级
     */
    public static void handleLivingAttack(@Nonnull LivingAttackEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        DamageSource source = event.getSource();
        LivingEntity victim = event.getEntity();

        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    /**
     * 处理受伤阶段（向后兼容，新EventHandler不使用此方法）
     *
     * @param event    事件
     * @param priority 优先级
     */
    public static void handleLivingHurt(@Nonnull LivingHurtEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        DamageSource source = event.getSource();
        LivingEntity victim = event.getEntity();

        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    /**
     * 处理伤害阶段（向后兼容，新EventHandler不使用此方法）
     *
     * @param event    事件
     * @param priority 优先级
     */
    public static void handleLivingDamage(@Nonnull LivingDamageEvent event, @Nonnull EventPriority priority) {
        if (event.getEntity().level().isClientSide) {
            return;
        }

        DamageSource source = event.getSource();
        LivingEntity victim = event.getEntity();

        if (source.getDirectEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity attacker) {
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    // ==================== 实体附魔处理（内部方法） ====================

    /**
     * 处理实体的附魔（原始遍历方式，保留向后兼容）
     */
    private static void processEntityEnchantments(
            @Nonnull LivingEntity holder,
            @Nullable LivingEntity victim,
            @Nullable DamageSource source,
            @Nonnull Object event,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        if (isAttacker) {
            ItemStack mainHand = holder.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = holder.getItemInHand(InteractionHand.OFF_HAND);

            if (!mainHand.isEmpty()) {
                processItemEnchantments(mainHand, holder, victim, source, event, priority, true);
            }
            if (!offHand.isEmpty()) {
                processItemEnchantments(offHand, holder, victim, source, event, priority, true);
            }

            for (EquipmentSlot slot : EquipmentSlots.SCAN) {
                if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                    continue;
                }
                ItemStack stack = holder.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    processItemEnchantments(stack, holder, victim, source, event, priority, true);
                }
            }
        } else {
            for (EquipmentSlot slot : EquipmentSlots.SCAN) {
                ItemStack stack = holder.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    processItemEnchantments(stack, holder, victim, source, event, priority, false);
                }
            }
        }
    }

    /**
     * 处理单个物品上的附魔
     */
    private static void processItemEnchantments(
            @Nonnull ItemStack stack,
            @Nonnull LivingEntity holder,
            @Nullable LivingEntity victim,
            @Nullable DamageSource source,
            @Nonnull Object event,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        Map<EnchantmentBase, Integer> enchantments = EnchantmentLevels.all(stack);

        for (EnchantmentBase baseEnchantment : enchantments.keySet()) {
            // v2.2：跳过被禁用的附魔
            if (baseEnchantment.isDisabled()) {
                continue;
            }

            int level = EnchantmentLevels.get(baseEnchantment, stack);
            level = baseEnchantment.applyLevelLimit(level);

            if (level <= 0) {
                continue;
            }

            LivingEntity attacker = isAttacker ? holder :
                    (source != null && source.getEntity() instanceof LivingEntity le ? le : null);

            EnchantmentContext ctx = new EnchantmentContext(
                    event, holder, stack, level,
                    attacker, victim, source
            );

            if (event instanceof LivingAttackEvent) {
                dispatchLivingAttackEvent(baseEnchantment, ctx, level, priority, isAttacker);
            } else if (event instanceof LivingHurtEvent) {
                dispatchLivingHurtEvent(baseEnchantment, ctx, level, priority, isAttacker);
            } else if (event instanceof LivingDamageEvent) {
                dispatchLivingDamageEvent(baseEnchantment, ctx, level, priority, isAttacker);
            }
        }
    }

    // ==================== 事件分发方法（v2.2：加入 isDisabled 前置检查） ====================

    /**
     * 分发攻击阶段到对应优先级的模板方法
     * <p>
     * v2.1变更：private → public，供 EnchantmentEventHandler 缓存机制直接调用
     * v2.2变更：加入 isDisabled() 前置检查
     * </p>
     *
     * @param enchantment 附魔实例
     * @param ctx         附魔上下文
     * @param level       附魔等级
     * @param priority    事件优先级
     * @param isAttacker  是否为攻击者视角
     */
    public static void dispatchLivingAttackEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        // v2.2：被禁用的附魔不触发任何效果
        if (enchantment.isDisabled()) {
            return;
        }

        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onAttackHighest(ctx, level);
                } else {
                    enchantment.onDefendHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onAttackHigh(ctx, level);
                } else {
                    enchantment.onDefendHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onAttack(ctx, level);
                } else {
                    enchantment.onDefend(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onAttackLow(ctx, level);
                } else {
                    enchantment.onDefendLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onAttackLowest(ctx, level);
                } else {
                    enchantment.onDefendLowest(ctx, level);
                }
                break;
        }
    }

    /**
     * 分发受伤阶段到对应优先级的模板方法
     * <p>
     * v2.1变更：private → public，供 EnchantmentEventHandler 缓存机制直接调用
     * v2.2变更：加入 isDisabled() 前置检查
     * </p>
     *
     * @param enchantment 附魔实例
     * @param ctx         附魔上下文
     * @param level       附魔等级
     * @param priority    事件优先级
     * @param isAttacker  是否为攻击者视角
     */
    public static void dispatchLivingHurtEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        // v2.2：被禁用的附魔不触发任何效果
        if (enchantment.isDisabled()) {
            return;
        }

        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerHighest(ctx, level);
                } else {
                    enchantment.onHurtAsVictimHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerHigh(ctx, level);
                } else {
                    enchantment.onHurtAsVictimHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onHurtAsAttacker(ctx, level);
                } else {
                    enchantment.onHurtAsVictim(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerLow(ctx, level);
                } else {
                    enchantment.onHurtAsVictimLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onHurtAsAttackerLowest(ctx, level);
                } else {
                    enchantment.onHurtAsVictimLowest(ctx, level);
                }
                break;
        }
    }

    /**
     * 分发伤害阶段到对应优先级的模板方法
     * <p>
     * v2.1变更：private → public，供 EnchantmentEventHandler 缓存机制直接调用
     * v2.2变更：加入 isDisabled() 前置检查
     * </p>
     *
     * @param enchantment 附魔实例
     * @param ctx         附魔上下文
     * @param level       附魔等级
     * @param priority    事件优先级
     * @param isAttacker  是否为攻击者视角
     */
    public static void dispatchLivingDamageEvent(
            @Nonnull EnchantmentBase enchantment,
            @Nonnull EnchantmentContext ctx,
            int level,
            @Nonnull EventPriority priority,
            boolean isAttacker
    ) {
        // v2.2：被禁用的附魔不触发任何效果
        if (enchantment.isDisabled()) {
            return;
        }

        switch (priority) {
            case HIGHEST:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerHighest(ctx, level);
                } else {
                    enchantment.onDamageAsVictimHighest(ctx, level);
                }
                break;
            case HIGH:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerHigh(ctx, level);
                } else {
                    enchantment.onDamageAsVictimHigh(ctx, level);
                }
                break;
            case NORMAL:
                if (isAttacker) {
                    enchantment.onDamageAsAttacker(ctx, level);
                } else {
                    enchantment.onDamageAsVictim(ctx, level);
                }
                break;
            case LOW:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerLow(ctx, level);
                } else {
                    enchantment.onDamageAsVictimLow(ctx, level);
                }
                break;
            case LOWEST:
                if (isAttacker) {
                    enchantment.onDamageAsAttackerLowest(ctx, level);
                } else {
                    enchantment.onDamageAsVictimLowest(ctx, level);
                }
                break;
        }
    }
}
