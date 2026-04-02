package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.entity.living.*;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
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
 * <p>
 * 修复记录 v2.1：
 * - dispatchLivingAttackEvent/HurtEvent/DamageEvent 从 private 改为 public，
 *   供优化后的 EnchantmentEventHandler 缓存机制直接调用
 * </p>
 * <p>
 * 修复记录 v2.2：
 * - 新增 isDisabled() 方法，读取 ConfigLoader.uninstallEnchantment 黑名单
 * - 重写 canApplyAtEnchantingTable / isAllowedOnBooks / isTradeable / isDiscoverable，
 *   被禁用的附魔无法从附魔台、书本、交易、战利品获得
 * - 三个 dispatch 方法加入 isDisabled() 前置检查，被禁用的附魔不会触发任何效果
 * - 新增静态工具方法 isEnchantmentDisabled(Enchantment)，供独立 @SubscribeEvent 方法使用
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
public abstract class EnchantmentBase extends Enchantment {

    protected final EnchantmentRarity enchantmentRarity;

    @Nullable
    protected final AutoRegisterEnchantment annotation;

    /**
     * 缓存禁用状态，避免每次调用都遍历数组
     * <p>
     * null = 未检查过（配置可能还没加载），true = 已确认禁用，false = 已确认启用
     * 配置重载后通过 invalidateDisabledCache() 清除缓存
     * </p>
     */
    private Boolean disabledCache = null;

    /**
     * 主构造函数（注解注册方式）
     *
     * @param category 附魔类别
     * @param slots    适用装备槽位
     */
    protected EnchantmentBase(@Nonnull EnchantmentCategory category, @Nonnull EquipmentSlot[] slots) {
        super(Enchantment.Rarity.COMMON, category, slots);

        this.annotation = this.getClass().getAnnotation(AutoRegisterEnchantment.class);

        if (annotation != null) {
            this.enchantmentRarity = annotation.rarity();
            CarianStyleEnchantments.ENCHANTMENTS.add(this);
            LogUtil.debug("卡利亚式附魔 - 通过注解注册附魔: %s", annotation.id());
        } else {
            this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
            LogUtil.warn("卡利亚式附魔 - 附魔类 %s 未使用AutoRegisterEnchantment注解",
                    this.getClass().getSimpleName());
        }
    }

    /**
     * 传统构造函数（向后兼容）
     *
     * @param rarityIn 稀有度
     * @param category 附魔类别
     * @param slots    适用装备槽位
     * @param name     附魔名称
     */
    protected EnchantmentBase(@Nonnull Enchantment.Rarity rarityIn, @Nonnull EnchantmentCategory category,
                              @Nonnull EquipmentSlot[] slots, String name) {
        super(rarityIn, category, slots);
        this.annotation = null;

        switch (rarityIn) {
            case RARE:
                this.enchantmentRarity = EnchantmentRarity.RARE;
                break;
            case VERY_RARE:
                this.enchantmentRarity = EnchantmentRarity.VERY_RARE;
                break;
            case UNCOMMON:
            default:
                this.enchantmentRarity = EnchantmentRarity.UNCOMMON;
        }

        CarianStyleEnchantments.ENCHANTMENTS.add(this);
        LogUtil.debug("卡利亚式附魔 - 通过传统方式注册附魔: %s", name);
    }

    // ==================== 禁用检查（v2.2新增） ====================

    /**
     * 检查此附魔是否被配置黑名单禁用
     * <p>
     * 读取 ConfigLoader.uninstallEnchantment 数组，匹配注解中的 id。
     * 结果会被缓存，配置重载后需调用 invalidateDisabledCache() 清除。
     * </p>
     *
     * @return true 表示此附魔已被禁用，不应产生任何效果
     */
    public boolean isDisabled() {
        // 无注解的附魔无法被禁用（没有id可匹配）
        if (annotation == null) {
            return false;
        }

        // 使用缓存避免每次遍历
        if (disabledCache != null) {
            return disabledCache;
        }

        // 遍历黑名单检查
        String[] blacklist = ConfigLoader.uninstallEnchantment;
        if (blacklist != null && blacklist.length > 0) {
            String myId = annotation.id();
            for (String disabledId : blacklist) {
                if (myId.equals(disabledId)) {
                    disabledCache = true;
                    return true;
                }
            }
        }

        disabledCache = false;
        return false;
    }

    /**
     * 清除禁用状态缓存（配置重载时调用）
     */
    public void invalidateDisabledCache() {
        disabledCache = null;
    }

    /**
     * 静态工具方法：检查任意 Enchantment 是否被禁用
     * <p>
     * 供独立 @SubscribeEvent 方法使用，例如：
     * <pre>
     * Enchantment ench = EnchantmentRegistry.getEnchantmentByClass(MyEnchantment.class);
     * if (EnchantmentBase.isEnchantmentDisabled(ench)) return;
     * </pre>
     * </p>
     *
     * @param enchantment 要检查的附魔
     * @return true 表示被禁用或为 null
     */
    public static boolean isEnchantmentDisabled(@Nullable Enchantment enchantment) {
        if (enchantment == null) {
            return true;
        }
        if (enchantment instanceof EnchantmentBase base) {
            return base.isDisabled();
        }
        return false;
    }

    /**
     * 清除所有已注册附魔的禁用缓存
     * <p>
     * 在 ConfigLoader.bake() 中调用，确保配置热重载后黑名单立即生效
     * </p>
     */
    public static void invalidateAllDisabledCaches() {
        for (Enchantment enchantment : CarianStyleEnchantments.ENCHANTMENTS) {
            if (enchantment instanceof EnchantmentBase base) {
                base.invalidateDisabledCache();
            }
        }
    }

    // ==================== 附魔台/战利品拦截（v2.2新增） ====================

    /**
     * 被禁用的附魔无法从附魔台获得
     */
    @Override
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        if (isDisabled()) {
            return false;
        }
        return super.canApplyAtEnchantingTable(stack);
    }

    /**
     * 被禁用的附魔无法附在书上
     */
    @Override
    public boolean isAllowedOnBooks() {
        if (isDisabled()) {
            return false;
        }
        return super.isAllowedOnBooks();
    }

    /**
     * 被禁用的附魔无法从村民交易获得
     */
    @Override
    public boolean isTradeable() {
        if (isDisabled()) {
            return false;
        }
        return super.isTradeable();
    }

    /**
     * 被禁用的附魔无法从战利品表中获得
     */
    @Override
    public boolean isDiscoverable() {
        if (isDisabled()) {
            return false;
        }
        return super.isDiscoverable();
    }

    // ==================== 原有方法（未修改） ====================

    @Override
    public int getMaxLevel() {
        return enchantmentRarity.getMaxLevel();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        if (annotation != null && annotation.baseEnchantability() != -1) {
            return (int) ((annotation.baseEnchantability() +
                    (enchantmentLevel - 1) * annotation.levelMultiplier()) *
                    ConfigLoader.enchantingDifficulty);
        }
        return enchantmentRarity.calculateEnchantability(enchantmentLevel, ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) * 2;
    }

    @Override
    public boolean isTreasureOnly() {
        if (annotation != null && annotation.forceTreasure()) {
            return true;
        }

        switch (enchantmentRarity) {
            case VERY_RARE:
                return ConfigLoader.isTreasureVeryRaryEnchantment || super.isTreasureOnly();
            case RARE:
                return ConfigLoader.isTreasureRaryEnchantment || super.isTreasureOnly();
            case UNCOMMON:
                return ConfigLoader.isTreasureUncommonEnchantment || super.isTreasureOnly();
            default:
                return super.isTreasureOnly();
        }
    }

    @Override
    public boolean isCurse() {
        if (annotation != null && annotation.isCurse()) {
            return true;
        }
        return super.isCurse();
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment other) {
        if (!super.checkCompatibility(other)) {
            return false;
        }

        if (annotation == null) {
            return true;
        }

        // 检查白名单：allowWith中的附魔始终兼容
        for (Class<?> allowedClass : annotation.allowWith()) {
            if (allowedClass.isInstance(other)) {
                return true;
            }
        }

        // 同类别互斥（GENERAL类别除外）
        AutoRegisterEnchantment otherAnnotation = other.getClass().getAnnotation(AutoRegisterEnchantment.class);
        if (otherAnnotation != null) {
            if (annotation.category() == otherAnnotation.category() &&
                    annotation.category() != pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL) {
                return false;
            }
        }

        // 检查黑名单：conflictsWith中的附魔互斥
        for (Class<?> conflictClass : annotation.conflictsWith()) {
            if (conflictClass.isInstance(other)) {
                return false;
            }
        }

        return true;
    }

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
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(this, mainHand));
        }

        if (!offHand.isEmpty()) {
            level = Math.max(level, EnchantmentHelper.getItemEnchantmentLevel(this, offHand));
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
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(this, armor);
            }
        }

        return applyLevelLimit(totalLevel);
    }

    /**
     * 应用附魔等级上限
     *
     * @param level 原始等级
     * @return 限制后的等级
     */
    public int applyLevelLimit(int level) {
        if (ConfigLoader.levelLimit) {
            return Math.min(level, 10);
        }
        return level;
    }

    /**
     * 判断玩家是否刚完成满蓄力攻击
     *
     * @param player 玩家
     * @return 是否满蓄力
     */
    protected boolean isJustSwung(@Nonnull Player player) {
        return player.getAttackStrengthScale(0.5f) == 1;
    }

    // ==================== LivingAttackEvent 模板方法 (10个) ====================

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

    // ==================== LivingHurtEvent 模板方法 (10个) ====================

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

    // ==================== LivingDamageEvent 模板方法 (10个) ====================

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
     * 处理 LivingAttackEvent（向后兼容，新EventHandler不使用此方法）
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

        if (source.getDirectEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getDirectEntity();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    /**
     * 处理 LivingHurtEvent（向后兼容，新EventHandler不使用此方法）
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

        if (source.getDirectEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getDirectEntity();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        }

        processEntityEnchantments(victim, victim, source, event, priority, false);
    }

    /**
     * 处理 LivingDamageEvent（向后兼容，新EventHandler不使用此方法）
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

        if (source.getDirectEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getDirectEntity();
            processEntityEnchantments(attacker, victim, source, event, priority, true);
        } else if (source.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) source.getEntity();
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

            for (EquipmentSlot slot : EquipmentSlot.values()) {
                if (slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) {
                    continue;
                }
                ItemStack stack = holder.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    processItemEnchantments(stack, holder, victim, source, event, priority, true);
                }
            }
        } else {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
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
        Map<Enchantment, Integer> enchantments = EnchantmentHelper.getEnchantments(stack);

        for (Enchantment enchantment : enchantments.keySet()) {
            if (!(enchantment instanceof EnchantmentBase)) {
                continue;
            }

            EnchantmentBase baseEnchantment = (EnchantmentBase) enchantment;

            // v2.2：跳过被禁用的附魔
            if (baseEnchantment.isDisabled()) {
                continue;
            }

            int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack);
            level = baseEnchantment.applyLevelLimit(level);

            if (level <= 0) {
                continue;
            }

            LivingEntity attacker = isAttacker ? holder :
                    (source != null && source.getEntity() instanceof LivingEntity ?
                     (LivingEntity) source.getEntity() : null);

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
     * 分发 LivingAttackEvent 到对应优先级的模板方法
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
     * 分发 LivingHurtEvent 到对应优先级的模板方法
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
     * 分发 LivingDamageEvent 到对应优先级的模板方法
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