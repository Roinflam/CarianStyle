package pers.roinflam.carianstyle.source;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import pers.roinflam.carianstyle.mc112.damage.CarianDamageSource;
import pers.roinflam.carianstyle.mc112.damage.CarianEntityDamageSource;
import pers.roinflam.carianstyle.mc112.damage.DamageTags;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自定义伤害来源
 * <p>
 * 1.20.1 的伤害类型定义在 {@code data/carianstyle/damage_type/*.json}，运行时从注册表取。
 * 1.12.2 版：没有伤害类型注册表，这里把那五个类型的数据（死亡信息键、标签、饥饿消耗、难度缩放）写成表，
 * 每次调用造一个新的伤害源对象（不复用单例，调用方可以放心改它的开关）。
 * 有实体时返回 {@link CarianEntityDamageSource}，没有时返回 {@link CarianDamageSource}。
 * </p>
 * <p>
 * 1.20.1 标签到 1.12.2 开关的换算：
 * </p>
 * <ul>
 *   <li>{@code bypasses_effects}（抗性和保护附魔都不算）→ 绝对伤害；</li>
 *   <li>{@code bypasses_resistance}（只无视抗性）→ 绝对伤害，保护附魔由 {@code DamageStageHook} 在受伤事件里补算；</li>
 *   <li>{@code witch_resistant_to} → 魔法伤害（女巫减免）；</li>
 *   <li>{@code bypasses_armor} → 没有实体的源构造时直接打开「无视护甲」（没有来源位置，盾牌本来就挡不住）；
 *       有实体的源只打「受伤阶段再无视护甲」标记，盾牌照常检查，和 1.20.1 一样。</li>
 * </ul>
 * <p>
 * 使用示例：
 * <pre>
 * // 简单伤害（无攻击者）
 * DamageSource epilepsyFire = NewDamageSource.get(world, NewDamageSource.EPILEPSY_FIRE);
 * entity.attackEntityFrom(epilepsyFire, 10.0F);
 *
 * // 带攻击者的伤害
 * DamageSource hemorrhage = NewDamageSource.get(world, NewDamageSource.HEMORRHAGE, attacker);
 * entity.attackEntityFrom(hemorrhage, 5.0F);
 * </pre>
 */
public class NewDamageSource {

    /**
     * 癫火伤害类型
     * <p>
     * 特性：无视护甲、无视效果、魔法伤害
     */
    public static final String EPILEPSY_FIRE = create("epilepsy_fire");

    /**
     * 冻伤伤害类型
     * <p>
     * 特性：普通物理伤害；被怪物打出来时按难度缩放
     */
    public static final String FROSTBITE = create("frostbite");

    /**
     * 猩红腐烂伤害类型
     * <p>
     * 特性：魔法伤害、无视护甲、无视抗性（保护附魔照算）
     */
    public static final String SCARLET_ROT = create("scarlet_rot");

    /**
     * 波石魔法伤害类型
     * <p>
     * 特性：普通魔法伤害
     */
    public static final String WAVE_STONE_MAGIC = create("wave_stone_magic");

    /**
     * 出血伤害类型
     * <p>
     * 特性：无视护甲、无视效果
     */
    public static final String HEMORRHAGE = create("hemorrhage");

    /** 类型 id → 类型数据（1.20.1 的 damage_type json + 标签） */
    private static final Map<String, Type> TYPES = new HashMap<>();

    static {
        TYPES.put(EPILEPSY_FIRE, new Type("unendurableFrenzy", 0.0F, false,
                DamageTags.BYPASSES_ARMOR, DamageTags.BYPASSES_EFFECTS, DamageTags.WITCH_RESISTANT_TO));
        TYPES.put(FROSTBITE, new Type("frostbite", 0.1F, true));
        TYPES.put(SCARLET_ROT, new Type("scarletRot", 0.0F, false,
                DamageTags.BYPASSES_ARMOR, DamageTags.BYPASSES_RESISTANCE, DamageTags.WITCH_RESISTANT_TO));
        TYPES.put(WAVE_STONE_MAGIC, new Type("wave_stone_magic", 0.0F, false,
                DamageTags.WITCH_RESISTANT_TO));
        TYPES.put(HEMORRHAGE, new Type("hemorrhage", 0.0F, false,
                DamageTags.BYPASSES_ARMOR, DamageTags.BYPASSES_EFFECTS));
    }

    /**
     * 拼出伤害类型 id
     *
     * @param name 伤害类型名称
     * @return {@code carianstyle:<name>}
     */
    private static String create(String name) {
        return Reference.MOD_ID + ":" + name;
    }

    // 私有构造函数，防止实例化
    private NewDamageSource() {
    }

    /**
     * 一个伤害类型的数据
     */
    private static final class Type {
        final String msgId;
        final float exhaustion;
        final boolean scaling;
        final Set<String> tags;

        Type(String msgId, float exhaustion, boolean scaling, String... tags) {
            this.msgId = msgId;
            this.exhaustion = exhaustion;
            this.scaling = scaling;
            this.tags = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(tags)));
        }
    }

    // ==================== 辅助方法：获取DamageSource实例 ====================

    /**
     * 获取简单伤害源（无攻击者）
     *
     * @param world         世界实例（1.12.2 用不上，保留是为了调用处和 1.20.1 一样）
     * @param damageTypeKey 伤害类型 id（本类常量）
     * @return 新的伤害源
     */
    @Nonnull
    public static DamageSource get(@Nonnull World world, @Nonnull String damageTypeKey) {
        return get(world, damageTypeKey, null, null);
    }

    /**
     * 获取带直接攻击者的伤害源
     *
     * @param world         世界实例
     * @param damageTypeKey 伤害类型 id
     * @param directEntity  直接造成伤害的实体（例如：投射物、攻击者本身）
     * @return 新的伤害源
     */
    @Nonnull
    public static DamageSource get(@Nonnull World world, @Nonnull String damageTypeKey, @Nullable Entity directEntity) {
        return get(world, damageTypeKey, directEntity, null);
    }

    /**
     * 获取带直接和间接攻击者的伤害源
     *
     * @param world         世界实例
     * @param damageTypeKey 伤害类型 id
     * @param directEntity  直接造成伤害的实体（例如：投射物）
     * @param causingEntity 间接造成伤害的实体（例如：射箭的玩家）
     * @return 新的伤害源
     */
    @Nonnull
    public static DamageSource get(@Nonnull World world, @Nonnull String damageTypeKey,
                                   @Nullable Entity directEntity, @Nullable Entity causingEntity) {
        Type type = TYPES.get(damageTypeKey);
        if (type == null) {
            throw new IllegalArgumentException("未知的伤害类型: " + damageTypeKey);
        }
        Entity direct = directEntity != null ? directEntity : causingEntity;
        DamageSource source;
        if (direct == null) {
            // 1.20.1 的冻伤是 when_caused_by_living_non_player：没有来源实体就不缩放
            source = new CarianDamageSource(damageTypeKey, type.msgId, type.tags, type.exhaustion, false);
            if (type.tags.contains(DamageTags.BYPASSES_ARMOR)) {
                source.setDamageBypassesArmor();
            }
        } else {
            CarianEntityDamageSource entitySource = new CarianEntityDamageSource(damageTypeKey, type.msgId, direct,
                    causingEntity, type.tags, type.exhaustion, type.scaling);
            entitySource.setBypassesArmorAtHurt(type.tags.contains(DamageTags.BYPASSES_ARMOR));
            source = entitySource;
        }
        if (type.tags.contains(DamageTags.BYPASSES_EFFECTS) || type.tags.contains(DamageTags.BYPASSES_RESISTANCE)
                || type.tags.contains(DamageTags.BYPASSES_ENCHANTMENTS)) {
            source.setDamageIsAbsolute();
        }
        if (type.tags.contains(DamageTags.WITCH_RESISTANT_TO)) {
            source.setMagicDamage();
        }
        return source;
    }

    // ==================== 便捷方法：常用伤害源的快速访问 ====================

    /**
     * 获取癫火伤害源
     *
     * @param world 世界实例
     * @return 癫火伤害源
     */
    @Nonnull
    public static DamageSource epilepsyFire(@Nonnull World world) {
        return get(world, EPILEPSY_FIRE);
    }

    /**
     * 获取癫火伤害源（带攻击者）
     *
     * @param world    世界实例
     * @param attacker 攻击者
     * @return 癫火伤害源
     */
    @Nonnull
    public static DamageSource epilepsyFire(@Nonnull World world, @Nullable Entity attacker) {
        return get(world, EPILEPSY_FIRE, attacker);
    }

    /**
     * 获取冻伤伤害源
     *
     * @param world 世界实例
     * @return 冻伤伤害源
     */
    @Nonnull
    public static DamageSource frostbite(@Nonnull World world) {
        return get(world, FROSTBITE);
    }

    /**
     * 获取冻伤伤害源（带攻击者）
     *
     * @param world    世界实例
     * @param attacker 攻击者
     * @return 冻伤伤害源
     */
    @Nonnull
    public static DamageSource frostbite(@Nonnull World world, @Nullable Entity attacker) {
        return get(world, FROSTBITE, attacker);
    }

    /**
     * 获取猩红腐烂伤害源
     *
     * @param world 世界实例
     * @return 猩红腐烂伤害源
     */
    @Nonnull
    public static DamageSource scarletRot(@Nonnull World world) {
        return get(world, SCARLET_ROT);
    }

    /**
     * 获取猩红腐烂伤害源（带攻击者）
     *
     * @param world    世界实例
     * @param attacker 攻击者
     * @return 猩红腐烂伤害源
     */
    @Nonnull
    public static DamageSource scarletRot(@Nonnull World world, @Nullable Entity attacker) {
        return get(world, SCARLET_ROT, attacker);
    }

    /**
     * 获取波石魔法伤害源
     *
     * @param world 世界实例
     * @return 波石魔法伤害源
     */
    @Nonnull
    public static DamageSource waveStoneMagic(@Nonnull World world) {
        return get(world, WAVE_STONE_MAGIC);
    }

    /**
     * 获取波石魔法伤害源（带攻击者）
     *
     * @param world    世界实例
     * @param attacker 攻击者
     * @return 波石魔法伤害源
     */
    @Nonnull
    public static DamageSource waveStoneMagic(@Nonnull World world, @Nullable Entity attacker) {
        return get(world, WAVE_STONE_MAGIC, attacker);
    }

    /**
     * 获取出血伤害源
     *
     * @param world 世界实例
     * @return 出血伤害源
     */
    @Nonnull
    public static DamageSource hemorrhage(@Nonnull World world) {
        return get(world, HEMORRHAGE);
    }

    /**
     * 获取出血伤害源（带攻击者）
     *
     * @param world    世界实例
     * @param attacker 攻击者
     * @return 出血伤害源
     */
    @Nonnull
    public static DamageSource hemorrhage(@Nonnull World world, @Nullable Entity attacker) {
        return get(world, HEMORRHAGE, attacker);
    }
}
