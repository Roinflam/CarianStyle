package pers.roinflam.carianstyle.mc112.damage;

import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.utils.util.LogUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 伤害源标签（顶替 1.20.1 的伤害类型标签和 {@code DamageSourceMixin} 的临时标签）
 * <p>
 * 常量名和 1.20.1 的 {@code DamageTypeTags} 同名，值是 1.20.1 的标签 id，这样 1.20.1 的
 * {@code source.is(DamageTypeTags.X)} 搬过来写成 {@code DamageTags.is(source, DamageTags.X)} 就行。
 * </p>
 * <p>
 * {@link #is} 认三种来源：
 * </p>
 * <ul>
 *   <li>临时标签：给某一个伤害源对象单独挂的（{@link #add}），存在弱引用表里，伤害源被回收就跟着没了；</li>
 *   <li>本模组新造的伤害源自带的标签（{@link ICarianDamageSource#builtinTags()}）；</li>
 *   <li>从 1.12.2 的开关推出来的：无视护甲 → {@code bypasses_armor}；绝对伤害且没挂只无视抗性 / 只无视附魔 →
 *       {@code bypasses_effects}；能伤创造模式 → {@code bypasses_invulnerability}；魔法 → {@code witch_resistant_to}；
 *       火 / 弹射物 / 爆炸 → 三个 {@code is_*}。所以对原版伤害源读标签也成立。</li>
 * </ul>
 * <p>
 * <b>绝不改共享单例</b>：{@code DamageSource.MAGIC}、{@code GENERIC}、{@code OUT_OF_WORLD} 这些是全服共用的同一个对象，
 * 给它挂标签或改开关等于改了以后所有同类伤害。启动时把 {@code DamageSource} 里所有 {@code public static final}
 * 伤害源收集起来，对它们的修改一律不做，只在详细日志里记一笔，需要的话由调用方另造一个伤害源。
 * 玩家近战、生物近战、箭这些每次都是新对象，可以直接改。
 * </p>
 */
public final class DamageTags {

    public static final String BYPASSES_ARMOR = "minecraft:bypasses_armor";
    public static final String BYPASSES_SHIELD = "minecraft:bypasses_shield";
    public static final String BYPASSES_EFFECTS = "minecraft:bypasses_effects";
    public static final String BYPASSES_RESISTANCE = "minecraft:bypasses_resistance";
    public static final String BYPASSES_ENCHANTMENTS = "minecraft:bypasses_enchantments";
    public static final String BYPASSES_INVULNERABILITY = "minecraft:bypasses_invulnerability";
    public static final String BYPASSES_COOLDOWN = "minecraft:bypasses_cooldown";
    public static final String WITCH_RESISTANT_TO = "minecraft:witch_resistant_to";
    public static final String IS_FIRE = "minecraft:is_fire";
    public static final String IS_PROJECTILE = "minecraft:is_projectile";
    public static final String IS_EXPLOSION = "minecraft:is_explosion";

    /** 临时标签。键按对象身份比较（{@code DamageSource} 没有覆写 equals），弱引用，访问加锁 */
    private static final Map<DamageSource, Set<String>> TEMPORARY = new WeakHashMap<>();

    /** {@code DamageSource} 里的全部共享单例，按 == 判断 */
    private static final Set<DamageSource> SHARED = Collections.newSetFromMap(new IdentityHashMap<>());

    static {
        for (Field field : DamageSource.class.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)
                    && DamageSource.class.isAssignableFrom(field.getType())) {
                try {
                    Object value = field.get(null);
                    if (value != null) {
                        SHARED.add((DamageSource) value);
                    }
                } catch (IllegalAccessException ignored) {
                    // public 字段，不会走到这里
                }
            }
        }
    }

    private DamageTags() {
    }

    /**
     * 伤害源是不是带这个标签（临时标签 ∪ 自带标签 ∪ 从开关推出的标签）
     *
     * @param source 伤害源
     * @param tag    标签（本类常量）
     * @return 带着为 true
     */
    public static boolean is(@Nonnull DamageSource source, @Nonnull String tag) {
        if (hasTemporary(source, tag)) {
            return true;
        }
        if (source instanceof ICarianDamageSource && ((ICarianDamageSource) source).builtinTags().contains(tag)) {
            return true;
        }
        switch (tag) {
            case BYPASSES_ARMOR:
                return source.isUnblockable();
            case BYPASSES_EFFECTS:
                return source.isDamageAbsolute() && !is(source, BYPASSES_RESISTANCE) && !is(source, BYPASSES_ENCHANTMENTS);
            case BYPASSES_INVULNERABILITY:
                return source.canHarmInCreative();
            case WITCH_RESISTANT_TO:
                return source.isMagicDamage();
            case IS_FIRE:
                return source.isFireDamage();
            case IS_PROJECTILE:
                return source.isProjectile();
            case IS_EXPLOSION:
                return source.isExplosion();
            default:
                return false;
        }
    }

    /**
     * 给这一个伤害源对象挂临时标签
     *
     * @param source 伤害源（共享单例不挂）
     * @param tag    标签
     */
    public static void add(@Nonnull DamageSource source, @Nonnull String tag) {
        if (isSharedSingleton(source)) {
            LogUtil.debug("卡利亚式附魔 - 不给共享伤害源 %s 挂标签 %s", source.getDamageType(), tag);
            return;
        }
        synchronized (TEMPORARY) {
            Set<String> tags = TEMPORARY.get(source);
            if (tags == null) {
                tags = new HashSet<>();
                TEMPORARY.put(source, tags);
            }
            tags.add(tag);
        }
    }

    /**
     * 删掉临时标签（自带标签和开关不动）
     *
     * @param source 伤害源
     * @param tag    标签
     */
    public static void remove(@Nonnull DamageSource source, @Nonnull String tag) {
        synchronized (TEMPORARY) {
            Set<String> tags = TEMPORARY.get(source);
            if (tags != null) {
                tags.remove(tag);
                if (tags.isEmpty()) {
                    TEMPORARY.remove(source);
                }
            }
        }
    }

    /**
     * 清掉这个伤害源的全部临时标签
     *
     * @param source 伤害源
     */
    public static void clear(@Nonnull DamageSource source) {
        synchronized (TEMPORARY) {
            TEMPORARY.remove(source);
        }
    }

    /**
     * 有没有这个临时标签
     *
     * @param source 伤害源
     * @param tag    标签
     * @return 挂着为 true
     */
    public static boolean hasTemporary(@Nonnull DamageSource source, @Nonnull String tag) {
        synchronized (TEMPORARY) {
            Set<String> tags = TEMPORARY.get(source);
            return tags != null && tags.contains(tag);
        }
    }

    /**
     * 是不是 {@code DamageSource} 里的共享单例（{@code MAGIC}、{@code GENERIC}……）
     *
     * @param source 伤害源
     * @return 是共享单例为 true，不许改它
     */
    public static boolean isSharedSingleton(@Nonnull DamageSource source) {
        return SHARED.contains(source);
    }
}
