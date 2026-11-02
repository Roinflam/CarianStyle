package pers.roinflam.carianstyle.base.enchantment;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.*;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.LogUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.utils.util.EquipmentSlots;

/**
 * PORT-STUB(B1a)
 *
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
 * <h3>修复记录 v2.3：附魔台权重与村民交易池污染</h3>
 * <p>
 * <b>问题一：所有附魔在原版权重体系里都是「最常见」。</b>
 * 本类的主构造函数把 {@code Enchantment.Rarity.COMMON} 硬编码传给了 super，
 * 而模组自己的 {@link EnchantmentRarity}（UNCOMMON / RARE / VERY_RARE）
 * 只被用来决定 {@link #getMaxLevel()} 与 {@link #getMinCost(int)}，
 * <b>从未参与原版的权重计算</b>。
 * </p>
 * <p>
 * 原版附魔台的候选池是加权抽取的：
 * {@code EnchantmentHelper.getAvailableEnchantmentResults} 收集候选后，
 * 交给 {@code WeightedRandom} 按 {@code EnchantmentInstance} 的权重抽取，
 * 而该权重来自 {@code enchantment.getRarity().getWeight()}
 * （COMMON=10、UNCOMMON=5、RARE=2、VERY_RARE=1）。
 * </p>
 * <p>
 * 于是本模组一百多个附魔<b>每一个都是权重 10</b>，
 * 和「效率 / 保护 / 锋利」这类最常见的原版附魔同档，
 * 而原版真正稀有的附魔只有 2 或 1。玩家在附魔台看到的自然全是模组附魔。
 * </p>
 * <p>
 * <b>修复：</b>覆写 {@link #getRarity()}，把模组稀有度映射回原版权重
 * （UNCOMMON→5、RARE→2、VERY_RARE→1）。
 * 之所以覆写 getter 而不是改构造函数，是因为 {@code Enchantment.rarity}
 * 是 private final、且 super() 调用时还拿不到子类注解，无法在构造期得到正确值；
 * 而 {@code getRarity()} 是 public 非 final 方法，原版所有读取点都走它。
 * </p>
 * <p>
 * <b>已知副作用：</b>铁砧的附魔书合成花费也读 {@code getRarity()}
 * （COMMON=1、UNCOMMON=2、RARE=4、VERY_RARE=8 倍率），
 * 因此 RARE / VERY_RARE 档的模组附魔在铁砧上会变贵。
 * 这与「稀有附魔更贵」的原版设计一致，但确实是这项映射带来的数值变化。
 * </p>
 *
 * <p>
 * <b>问题二：村民附魔书交易池被稀释。</b>
 * 1.20.1 的 {@code VillagerTrades.EnchantBookForEmeralds} 不看权重——
 * 它把所有 {@code isTradeable()} 为 true 的附魔收集成一个 List，然后<b>等概率</b>随机抽一个。
 * 本模组一百多个附魔默认全部可交易，直接把原版约 40 个可交易附魔的池子撑到 150+，
 * 「经验修补」的出现概率因此掉到原来的四分之一左右。
 * </p>
 * <p>
 * <b>修复：</b>覆写 {@link #isTradeable()} 返回 false，
 * 模组附魔不再进入图书管理员的附魔书交易池。
 * 附魔台、战利品箱的获取途径不受影响（由 {@link #isDiscoverable()} 控制，未改动）。
 * </p>
 *
 * <h3>v2.4：两项修复改为配置开关控制</h3>
 * <ul>
 *   <li>{@code ConfigLoader.useVanillaRarityWeight}（默认 true）——
 *       控制 {@link #getRarity()} 是否做权重映射。设为 false 恢复旧行为（全部权重 10），
 *       同时也会撤销铁砧变贵的副作用。</li>
 *   <li>{@code ConfigLoader.allowVillagerBookTrade}（默认 false）——
 *       控制 {@link #isTradeable()}。设为 true 恢复原版行为，模组附魔重新进入交易池。</li>
 * </ul>
 * <p>
 * 两项均在 {@code ConfigLoader.bake()} 中同步，支持热重载。
 * </p>
 *
 * @author RoinFlam
 * @version 2.4
 */
@Deprecated
public abstract class EnchantmentBase {
    protected EnchantmentRarity enchantmentRarity;
    @Nullable protected AutoRegisterEnchantment annotation;
    public boolean isDisabled() {
        return false;
    }
    public void invalidateDisabledCache() {
    }
    public static boolean isEnchantmentDisabled(@Nullable Enchantment enchantment) {
        return false;
    }
    public static void invalidateAllDisabledCaches() {
    }
    public boolean canApplyAtEnchantingTable(@Nonnull ItemStack stack) {
        return false;
    }
    public boolean isAllowedOnBooks() {
        return false;
    }
    public boolean isTradeable() {
        return false;
    }
    public boolean isDiscoverable() {
        return false;
    }
    public int getMaxLevel() {
        return 0;
    }
    public Component getFullname(int level) {
        return null;
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
    public boolean isTreasureOnly() {
        return false;
    }
    public boolean isCurse() {
        return false;
    }
    protected boolean checkCompatibility(@Nonnull Enchantment other) {
        return false;
    }
    protected int getEnchantmentLevelFromWeapon(@Nonnull LivingEntity entity) {
        return 0;
    }
    protected int getEnchantmentLevelFromArmor(@Nonnull LivingEntity entity) {
        return 0;
    }
    public int applyLevelLimit(int level) {
        return 0;
    }
    public int applyLevelLimit(int level, int cap) {
        return 0;
    }
    public static int applyLevelLimit(Enchantment enchantment, int level, int cap) {
        return 0;
    }
    protected int highestLevel(@Nonnull LivingEntity holder) {
        return 0;
    }
    public static int capLevel(int level, int cap) {
        return 0;
    }
    public static int decayLevel(Enchantment enchantment, int level) {
        return 0;
    }
    protected boolean isJustSwung(@Nonnull Player player) {
        return false;
    }
    public static boolean isFullyCharged(@Nullable LivingEntity holder) {
        return false;
    }
    public static boolean isSameKindOrAlly(@Nullable LivingEntity attacker, @Nonnull LivingEntity other) {
        return false;
    }
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
    protected void onDeath(@Nonnull EnchantmentContext ctx, int level) {
    }
    protected void onHeal(@Nonnull EnchantmentContext ctx, int level) {
    }
    protected void onPlayerTick(@Nonnull EnchantmentContext ctx, int level) {
    }
    protected void onCriticalHit(@Nonnull EnchantmentContext ctx, int level) {
    }
    public static void handleLivingAttack(@Nonnull LivingAttackEvent event, @Nonnull EventPriority priority) {
    }
    public static void handleLivingHurt(@Nonnull LivingHurtEvent event, @Nonnull EventPriority priority) {
    }
    public static void handleLivingDamage(@Nonnull LivingDamageEvent event, @Nonnull EventPriority priority) {
    }
    public static void dispatchLivingAttackEvent(@Nonnull EnchantmentBase enchantment, @Nonnull EnchantmentContext ctx, int level, @Nonnull EventPriority priority, boolean isAttacker) {
    }
    public static void dispatchLivingHurtEvent(@Nonnull EnchantmentBase enchantment, @Nonnull EnchantmentContext ctx, int level, @Nonnull EventPriority priority, boolean isAttacker) {
    }
    public static void dispatchLivingDamageEvent(@Nonnull EnchantmentBase enchantment, @Nonnull EnchantmentContext ctx, int level, @Nonnull EventPriority priority, boolean isAttacker) {
    }
}

