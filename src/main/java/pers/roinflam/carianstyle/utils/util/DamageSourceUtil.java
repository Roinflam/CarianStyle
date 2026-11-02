package pers.roinflam.carianstyle.utils.util;

import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.mc112.damage.ICarianDamageSource;
import pers.roinflam.carianstyle.mc112.util.Reflect;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * 伤害源工具类
 * <p>
 * 用于判断伤害类型和特性，以及动态修改DamageSource属性
 * </p>
 * <p>
 * 1.12.2 版：方法名照 1.20.1，标签参数从 {@code TagKey<DamageType>} 换成 {@link DamageTags} 的字符串常量。
 * 1.20.1 是靠 mixin 给单个伤害源挂临时标签；1.12.2 的 {@code DamageSource} 本来就有一组可改的开关，
 * 能用开关表达的标签直接改开关（无视护甲、绝对伤害、魔法、火、弹射物、爆炸、能伤创造模式），
 * 开关表达不了的（无视无敌帧、只无视抗性 / 只无视附魔的那一半）挂临时标签，由 {@code mc112.hook.DamageStageHook} 处理。
 * </p>
 * <p>
 * 时机要注意：1.12.2 的「无视护甲」开关同时让盾牌失效。在 {@code LivingAttackEvent} 或更早打开，
 * 等于「无视护甲 + 无视盾牌」；在 {@code LivingHurtEvent} 里打开只无视护甲（盾牌检查已经过了），
 * 和 1.20.1 在受伤阶段临时挂 {@code bypasses_armor} 的效果一样，所以附魔照 1.20.1 在 {@code onHurtAsAttacker} 里调
 * {@link #setBypassesArmor} 即可。类型本身在 {@code bypasses_armor} 里的伤害不同：1.20.1 的 {@code bypasses_shield}
 * 包含 {@code #bypasses_armor}，盾牌也挡不住，要在造伤害源时就打开。
 * </p>
 * <p>
 * {@code DamageSource.MAGIC} 这类全服共享的单例一律不改（见 {@link DamageTags} 的类注释），改了会影响以后所有同类伤害。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public class DamageSourceUtil {

    /** {@code DamageSource.isUnblockable}，写回 false 用 */
    private static final Field IS_UNBLOCKABLE = Reflect.field(DamageSource.class, "isUnblockable", "field_76374_o");

    /** {@code DamageSource.magicDamage}，清掉魔法标记用 */
    private static final Field MAGIC_DAMAGE = Reflect.field(DamageSource.class, "magicDamage", "field_82730_x");

    // ==================== 伤害类型判断 ====================

    /**
     * 判断是否为魔法伤害
     *
     * @param source 伤害源
     * @return 是否为魔法伤害
     */
    public static boolean isMagicDamage(@Nonnull DamageSource source) {
        String msgId = source.getDamageType().toLowerCase();
        return msgId.contains("magic") || DamageTags.is(source, DamageTags.WITCH_RESISTANT_TO);
    }

    /**
     * 判断是否为火焰伤害
     *
     * @param source 伤害源
     * @return 是否为火焰伤害
     */
    public static boolean isFireDamage(@Nonnull DamageSource source) {
        return DamageTags.is(source, DamageTags.IS_FIRE);
    }

    /**
     * 判断是否为物理伤害
     *
     * @param source 伤害源
     * @return 是否为物理伤害
     */
    public static boolean isPhysicalDamage(@Nonnull DamageSource source) {
        return !isMagicDamage(source);
    }

    /**
     * 判断是否为投射物伤害
     *
     * @param source 伤害源
     * @return 是否为投射物伤害
     */
    public static boolean isProjectileDamage(@Nonnull DamageSource source) {
        return DamageTags.is(source, DamageTags.IS_PROJECTILE);
    }

    /**
     * 判断是否为爆炸伤害
     *
     * @param source 伤害源
     * @return 是否为爆炸伤害
     */
    public static boolean isExplosionDamage(@Nonnull DamageSource source) {
        return DamageTags.is(source, DamageTags.IS_EXPLOSION);
    }

    // ==================== 动态属性修改（类似1.12.2） ====================

    /**
     * 设置伤害无视护甲
     * <p>
     * 1.12.2 版：直接打开 {@code setDamageBypassesArmor()}，只对当前这个伤害源对象有效。时机见类注释。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesArmor(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_ARMOR)) {
            return source;
        }
        source.setDamageBypassesArmor();
        return source;
    }

    /**
     * 设置伤害无视护甲（可选）
     * <p>
     * 1.12.2 版：传 false 时把开关写回关着、去掉「受伤阶段再无视护甲」标记和临时标签。
     * </p>
     *
     * @param source        伤害源
     * @param bypassesArmor 是否无视护甲
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesArmor(@Nonnull DamageSource source, boolean bypassesArmor) {
        if (bypassesArmor) {
            return setBypassesArmor(source);
        }
        if (refuseShared(source, DamageTags.BYPASSES_ARMOR)) {
            return source;
        }
        try {
            IS_UNBLOCKABLE.setBoolean(source, false);
        } catch (IllegalAccessException e) {
            LogUtil.debug("卡利亚式附魔 - 写不回无视护甲开关", e);
        }
        if (source instanceof ICarianDamageSource) {
            ((ICarianDamageSource) source).setBypassesArmorAtHurt(false);
        }
        DamageTags.remove(source, DamageTags.BYPASSES_ARMOR);
        return source;
    }

    /**
     * 设置伤害无视盾牌
     * <p>
     * 1.12.2 版：盾牌只认「无视护甲」这一个开关，所以和 {@link #setBypassesArmor} 是同一个开关；
     * 要在攻击阶段（{@code LivingAttackEvent} 或造成伤害之前）调才挡得住盾牌，这时护甲也一起无视了。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesShield(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_SHIELD)) {
            return source;
        }
        source.setDamageBypassesArmor();
        DamageTags.add(source, DamageTags.BYPASSES_SHIELD);
        return source;
    }

    /**
     * 设置伤害无视无敌状态
     * <p>
     * 1.12.2 版：打开 {@code setDamageAllowedInCreativeMode()}（能伤创造模式）。
     * 1.20.1 {@code invulnerable} 标记挡住的那部分 1.12.2 只有虚空伤害过得去，这里做不到。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesInvulnerability(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_INVULNERABILITY)) {
            return source;
        }
        source.setDamageAllowedInCreativeMode();
        return source;
    }

    /**
     * 设置伤害无视无敌帧
     * <p>
     * 1.12.2 版：挂临时标签，{@code DamageStageHook} 在攻击事件里把目标的无敌帧清零。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesCooldown(@Nonnull DamageSource source) {
        DamageTags.add(source, DamageTags.BYPASSES_COOLDOWN);
        return source;
    }

    /**
     * 设置伤害无视效果（如抗性提升）
     * <p>
     * 1.12.2 版：绝对伤害（{@code setDamageIsAbsolute()}），抗性提升和保护类附魔都不算。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesEffects(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_EFFECTS)) {
            return source;
        }
        source.setDamageIsAbsolute();
        return source;
    }

    /**
     * 设置伤害无视抗性
     * <p>
     * 1.12.2 版：绝对伤害 + 临时标签，保护附魔由 {@code DamageStageHook} 在受伤事件里补算回来。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesResistance(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_RESISTANCE)) {
            return source;
        }
        source.setDamageIsAbsolute();
        DamageTags.add(source, DamageTags.BYPASSES_RESISTANCE);
        return source;
    }

    /**
     * 设置伤害无视附魔
     * <p>
     * 1.12.2 版：绝对伤害 + 临时标签，抗性提升由 {@code DamageStageHook} 在受伤事件里补算回来。
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setBypassesEnchantments(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.BYPASSES_ENCHANTMENTS)) {
            return source;
        }
        source.setDamageIsAbsolute();
        DamageTags.add(source, DamageTags.BYPASSES_ENCHANTMENTS);
        return source;
    }

    /**
     * 设置为魔法伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setMagicDamage(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.WITCH_RESISTANT_TO)) {
            return source;
        }
        source.setMagicDamage();
        return source;
    }

    /**
     * 设置为火焰伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setFireDamage(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.IS_FIRE)) {
            return source;
        }
        source.setFireDamage();
        return source;
    }

    /**
     * 设置为投射物伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setProjectileDamage(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.IS_PROJECTILE)) {
            return source;
        }
        source.setProjectile();
        return source;
    }

    /**
     * 设置为爆炸伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource setExplosionDamage(@Nonnull DamageSource source) {
        if (refuseShared(source, DamageTags.IS_EXPLOSION)) {
            return source;
        }
        source.setExplosion();
        return source;
    }

    /**
     * 添加自定义标签
     * <p>
     * 1.12.2 版：能用开关表达的标签直接改开关，其余挂临时标签。
     * </p>
     *
     * @param source 伤害源
     * @param tag    要添加的标签（{@link DamageTags} 的常量）
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource addTag(@Nonnull DamageSource source, @Nonnull String tag) {
        switch (tag) {
            case DamageTags.BYPASSES_ARMOR:
                return setBypassesArmor(source);
            case DamageTags.BYPASSES_SHIELD:
                return setBypassesShield(source);
            case DamageTags.BYPASSES_INVULNERABILITY:
                return setBypassesInvulnerability(source);
            case DamageTags.BYPASSES_COOLDOWN:
                return setBypassesCooldown(source);
            case DamageTags.BYPASSES_EFFECTS:
                return setBypassesEffects(source);
            case DamageTags.BYPASSES_RESISTANCE:
                return setBypassesResistance(source);
            case DamageTags.BYPASSES_ENCHANTMENTS:
                return setBypassesEnchantments(source);
            case DamageTags.WITCH_RESISTANT_TO:
                return setMagicDamage(source);
            case DamageTags.IS_FIRE:
                return setFireDamage(source);
            case DamageTags.IS_PROJECTILE:
                return setProjectileDamage(source);
            case DamageTags.IS_EXPLOSION:
                return setExplosionDamage(source);
            default:
                DamageTags.add(source, tag);
                return source;
        }
    }

    /**
     * 移除临时标签
     * <p>
     * 1.12.2 版：删的是临时标签；对 {@link DamageTags#WITCH_RESISTANT_TO} 另外把伤害源的魔法标记清掉
     * （女巫不再减免），共享单例除外。1.20.1 里伤害类型自带的魔法标签删不掉，挥石魔法在 1.12.2 上
     * 因此会真的去掉魔法属性，这是有意保留的 1.12.2 行为。
     * </p>
     *
     * @param source 伤害源
     * @param tag    要移除的标签
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource removeTag(@Nonnull DamageSource source, @Nonnull String tag) {
        DamageTags.remove(source, tag);
        if (DamageTags.WITCH_RESISTANT_TO.equals(tag) && !refuseShared(source, tag)) {
            try {
                MAGIC_DAMAGE.setBoolean(source, false);
            } catch (IllegalAccessException e) {
                LogUtil.debug("卡利亚式附魔 - 清不掉魔法标记", e);
            }
        }
        return source;
    }

    /**
     * 清除所有临时标签
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @Nonnull
    public static DamageSource clearTags(@Nonnull DamageSource source) {
        DamageTags.clear(source);
        return source;
    }

    /**
     * 检查是否有某个临时标签
     *
     * @param source 伤害源
     * @param tag    要检查的标签
     * @return 是否有该临时标签
     */
    public static boolean hasTemporaryTag(@Nonnull DamageSource source, @Nonnull String tag) {
        return DamageTags.hasTemporary(source, tag);
    }

    /**
     * 共享单例不许改，记一笔日志
     *
     * @return 是共享单例（调用方应直接返回）
     */
    private static boolean refuseShared(@Nonnull DamageSource source, @Nonnull String what) {
        if (DamageTags.isSharedSingleton(source)) {
            LogUtil.debug("卡利亚式附魔 - 共享伤害源 %s 不改（%s）", source.getDamageType(), what);
            return true;
        }
        return false;
    }
}
