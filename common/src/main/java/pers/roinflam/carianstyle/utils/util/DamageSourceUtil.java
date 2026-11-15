package pers.roinflam.carianstyle.utils.util;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.api.accessor.DamageSourceAccessor;

/**
 * 伤害源工具类
 * <p>
 * 用于判断伤害类型和特性，以及动态修改DamageSource属性
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
public class DamageSourceUtil {

    // ==================== 伤害类型判断 ====================

    /**
     * 判断是否为魔法伤害
     *
     * @param source 伤害源
     * @return 是否为魔法伤害
     */
    public static boolean isMagicDamage(@NotNull DamageSource source) {
        String msgId = source.getMsgId().toLowerCase();
        return msgId.contains("magic") || source.is(DamageTypeTags.WITCH_RESISTANT_TO);
    }

    /**
     * 判断是否为火焰伤害
     *
     * @param source 伤害源
     * @return 是否为火焰伤害
     */
    public static boolean isFireDamage(@NotNull DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE);
    }

    /**
     * 判断是否为物理伤害
     *
     * @param source 伤害源
     * @return 是否为物理伤害
     */
    public static boolean isPhysicalDamage(@NotNull DamageSource source) {
        return !isMagicDamage(source);
    }

    /**
     * 判断是否为投射物伤害
     *
     * @param source 伤害源
     * @return 是否为投射物伤害
     */
    public static boolean isProjectileDamage(@NotNull DamageSource source) {
        return source.is(DamageTypeTags.IS_PROJECTILE);
    }

    /**
     * 判断是否为爆炸伤害
     *
     * @param source 伤害源
     * @return 是否为爆炸伤害
     */
    public static boolean isExplosionDamage(@NotNull DamageSource source) {
        return source.is(DamageTypeTags.IS_EXPLOSION);
    }

    // ==================== 动态属性修改（类似1.12.2） ====================

    /**
     * 设置伤害无视护甲
     * <p>
     * 通过Mixin添加临时标签，只对当前DamageSource实例有效
     * </p>
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesArmor(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_ARMOR);
        return source;
    }

    /**
     * 设置伤害无视护甲（可选）
     *
     * @param source         伤害源
     * @param bypassesArmor 是否无视护甲
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesArmor(@NotNull DamageSource source, boolean bypassesArmor) {
        if (bypassesArmor) {
            return setBypassesArmor(source);
        }
        return source;
    }

    /**
     * 设置伤害无视盾牌
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesShield(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_SHIELD);
        return source;
    }

    /**
     * 设置伤害无视无敌状态
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesInvulnerability(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return source;
    }

    /**
     * 设置伤害无视无敌帧
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesCooldown(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_COOLDOWN);
        return source;
    }

    /**
     * 设置伤害无视效果（如抗性提升）
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesEffects(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_EFFECTS);
        return source;
    }

    /**
     * 设置伤害无视抗性
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesResistance(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_RESISTANCE);
        return source;
    }

    /**
     * 设置伤害无视附魔
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setBypassesEnchantments(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.BYPASSES_ENCHANTMENTS);
        return source;
    }

    /**
     * 设置为魔法伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setMagicDamage(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.WITCH_RESISTANT_TO);
        return source;
    }

    /**
     * 设置为火焰伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setFireDamage(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.IS_FIRE);
        return source;
    }

    /**
     * 设置为投射物伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setProjectileDamage(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.IS_PROJECTILE);
        return source;
    }

    /**
     * 设置为爆炸伤害
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource setExplosionDamage(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(DamageTypeTags.IS_EXPLOSION);
        return source;
    }

    /**
     * 添加自定义标签
     *
     * @param source 伤害源
     * @param tag    要添加的标签
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource addTag(@NotNull DamageSource source, @NotNull TagKey<DamageType> tag) {
        ((DamageSourceAccessor) source).carianstyle$addTemporaryTag(tag);
        return source;
    }

    /**
     * 移除临时标签
     *
     * @param source 伤害源
     * @param tag    要移除的标签
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource removeTag(@NotNull DamageSource source, @NotNull TagKey<DamageType> tag) {
        ((DamageSourceAccessor) source).carianstyle$removeTemporaryTag(tag);
        return source;
    }

    /**
     * 清除所有临时标签
     *
     * @param source 伤害源
     * @return 修改后的伤害源（链式调用）
     */
    @NotNull
    public static DamageSource clearTags(@NotNull DamageSource source) {
        ((DamageSourceAccessor) source).carianstyle$clearTemporaryTags();
        return source;
    }

    /**
     * 检查是否有某个临时标签
     *
     * @param source 伤害源
     * @param tag    要检查的标签
     * @return 是否有该临时标签
     */
    public static boolean hasTemporaryTag(@NotNull DamageSource source, @NotNull TagKey<DamageType> tag) {
        return ((DamageSourceAccessor) source).carianstyle$hasTemporaryTag(tag);
    }
}