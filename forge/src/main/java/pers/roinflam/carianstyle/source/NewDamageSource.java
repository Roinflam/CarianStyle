package pers.roinflam.carianstyle.source;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import pers.roinflam.carianstyle.utils.Reference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 自定义伤害来源
 * <p>
 * ⚠️ 1.20.1重要变更：伤害系统完全重构
 * <p>
 * 新系统工作流程：
 * 1. 在 data/{modid}/damage_type/*.json 中定义伤害类型
 * 2. 创建 ResourceKey 引用这些伤害类型
 * 3. 运行时通过注册表获取 DamageSource 实例
 * <p>
 * 使用示例：
 * <pre>
 * // 简单伤害（无攻击者）
 * DamageSource epilepsyFire = NewDamageSource.get(level, NewDamageSource.EPILEPSY_FIRE);
 * entity.hurt(epilepsyFire, 10.0F);
 *
 * // 带攻击者的伤害
 * DamageSource hemorrhage = NewDamageSource.get(level, NewDamageSource.HEMORRHAGE, attacker);
 * entity.hurt(hemorrhage, 5.0F);
 * </pre>
 */
public class NewDamageSource {

    /**
     * 癫火伤害类型
     * <p>
     * 特性：无视护甲、魔法伤害
     * <p>
     * 数据文件位置：data/carianstyle/damage_type/epilepsy_fire.json
     */
    public static final ResourceKey<DamageType> EPILEPSY_FIRE = create("epilepsy_fire");

    /**
     * 冻伤伤害类型
     * <p>
     * 特性：普通物理伤害
     * <p>
     * 数据文件位置：data/carianstyle/damage_type/frostbite.json
     */
    public static final ResourceKey<DamageType> FROSTBITE = create("frostbite");

    /**
     * 猩红腐烂伤害类型
     * <p>
     * 特性：魔法伤害、无视护甲
     * <p>
     * 数据文件位置：data/carianstyle/damage_type/scarlet_rot.json
     */
    public static final ResourceKey<DamageType> SCARLET_ROT = create("scarlet_rot");

    /**
     * 波石魔法伤害类型
     * <p>
     * 特性：普通魔法伤害
     * <p>
     * 数据文件位置：data/carianstyle/damage_type/wave_stone_magic.json
     */
    public static final ResourceKey<DamageType> WAVE_STONE_MAGIC = create("wave_stone_magic");

    /**
     * 出血伤害类型
     * <p>
     * 特性：无视护甲
     * <p>
     * 数据文件位置：data/carianstyle/damage_type/hemorrhage.json
     */
    public static final ResourceKey<DamageType> HEMORRHAGE = create("hemorrhage");

    /**
     * 创建伤害类型的ResourceKey
     *
     * @param name 伤害类型名称
     * @return 伤害类型的ResourceKey
     */
    private static ResourceKey<DamageType> create(String name) {
        return ResourceKey.create(
                Registries.DAMAGE_TYPE,
                new ResourceLocation(Reference.MOD_ID, name)
        );
    }

    // 私有构造函数，防止实例化
    private NewDamageSource() {
    }

    // ==================== 辅助方法：获取DamageSource实例 ====================

    /**
     * 获取简单伤害源（无攻击者）
     * <p>
     * 由于DamageSources.source()是私有方法，我们需要手动构造DamageSource
     * <p>
     * 使用示例：
     * <pre>
     * DamageSource source = NewDamageSource.get(level, NewDamageSource.EPILEPSY_FIRE);
     * entity.hurt(source, 10.0F);
     * </pre>
     *
     * @param level 世界实例
     * @param damageTypeKey 伤害类型Key
     * @return DamageSource实例
     */
    @Nonnull
    public static DamageSource get(@Nonnull Level level, @Nonnull ResourceKey<DamageType> damageTypeKey) {
        // 从注册表获取DamageType的Holder
        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageTypeKey);

        // 构造DamageSource
        return new DamageSource(holder);
    }

    /**
     * 获取带直接攻击者的伤害源
     * <p>
     * 使用示例：
     * <pre>
     * DamageSource source = NewDamageSource.get(level, NewDamageSource.HEMORRHAGE, attacker);
     * entity.hurt(source, 5.0F);
     * </pre>
     *
     * @param level 世界实例
     * @param damageTypeKey 伤害类型Key
     * @param directEntity 直接造成伤害的实体（例如：投射物、攻击者本身）
     * @return DamageSource实例
     */
    @Nonnull
    public static DamageSource get(
            @Nonnull Level level,
            @Nonnull ResourceKey<DamageType> damageTypeKey,
            @Nullable Entity directEntity) {

        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageTypeKey);

        return new DamageSource(holder, directEntity);
    }

    /**
     * 获取带直接和间接攻击者的伤害源
     * <p>
     * 使用示例：
     * <pre>
     * // 弓箭伤害：arrow是直接实体，shooter是间接实体
     * DamageSource source = NewDamageSource.get(level, NewDamageSource.FROSTBITE, arrow, shooter);
     * entity.hurt(source, 8.0F);
     * </pre>
     *
     * @param level 世界实例
     * @param damageTypeKey 伤害类型Key
     * @param directEntity 直接造成伤害的实体（例如：投射物）
     * @param causingEntity 间接造成伤害的实体（例如：射箭的玩家）
     * @return DamageSource实例
     */
    @Nonnull
    public static DamageSource get(
            @Nonnull Level level,
            @Nonnull ResourceKey<DamageType> damageTypeKey,
            @Nullable Entity directEntity,
            @Nullable Entity causingEntity) {

        Holder<DamageType> holder = level.registryAccess()
                .registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(damageTypeKey);

        return new DamageSource(holder, directEntity, causingEntity);
    }

    // ==================== 便捷方法：常用伤害源的快速访问 ====================

    /**
     * 获取癫火伤害源
     *
     * @param level 世界实例
     * @return 癫火伤害源
     */
    @Nonnull
    public static DamageSource epilepsyFire(@Nonnull Level level) {
        return get(level, EPILEPSY_FIRE);
    }

    /**
     * 获取癫火伤害源（带攻击者）
     *
     * @param level 世界实例
     * @param attacker 攻击者
     * @return 癫火伤害源
     */
    @Nonnull
    public static DamageSource epilepsyFire(@Nonnull Level level, @Nullable Entity attacker) {
        return get(level, EPILEPSY_FIRE, attacker);
    }

    /**
     * 获取冻伤伤害源
     *
     * @param level 世界实例
     * @return 冻伤伤害源
     */
    @Nonnull
    public static DamageSource frostbite(@Nonnull Level level) {
        return get(level, FROSTBITE);
    }

    /**
     * 获取冻伤伤害源（带攻击者）
     *
     * @param level 世界实例
     * @param attacker 攻击者
     * @return 冻伤伤害源
     */
    @Nonnull
    public static DamageSource frostbite(@Nonnull Level level, @Nullable Entity attacker) {
        return get(level, FROSTBITE, attacker);
    }

    /**
     * 获取猩红腐烂伤害源
     *
     * @param level 世界实例
     * @return 猩红腐烂伤害源
     */
    @Nonnull
    public static DamageSource scarletRot(@Nonnull Level level) {
        return get(level, SCARLET_ROT);
    }

    /**
     * 获取猩红腐烂伤害源（带攻击者）
     *
     * @param level 世界实例
     * @param attacker 攻击者
     * @return 猩红腐烂伤害源
     */
    @Nonnull
    public static DamageSource scarletRot(@Nonnull Level level, @Nullable Entity attacker) {
        return get(level, SCARLET_ROT, attacker);
    }

    /**
     * 获取波石魔法伤害源
     *
     * @param level 世界实例
     * @return 波石魔法伤害源
     */
    @Nonnull
    public static DamageSource waveStoneMagic(@Nonnull Level level) {
        return get(level, WAVE_STONE_MAGIC);
    }

    /**
     * 获取波石魔法伤害源（带攻击者）
     *
     * @param level 世界实例
     * @param attacker 攻击者
     * @return 波石魔法伤害源
     */
    @Nonnull
    public static DamageSource waveStoneMagic(@Nonnull Level level, @Nullable Entity attacker) {
        return get(level, WAVE_STONE_MAGIC, attacker);
    }

    /**
     * 获取出血伤害源
     *
     * @param level 世界实例
     * @return 出血伤害源
     */
    @Nonnull
    public static DamageSource hemorrhage(@Nonnull Level level) {
        return get(level, HEMORRHAGE);
    }

    /**
     * 获取出血伤害源（带攻击者）
     *
     * @param level 世界实例
     * @param attacker 攻击者
     * @return 出血伤害源
     */
    @Nonnull
    public static DamageSource hemorrhage(@Nonnull Level level, @Nullable Entity attacker) {
        return get(level, HEMORRHAGE, attacker);
    }
}