package pers.roinflam.carianstyle.source;

import net.minecraft.util.DamageSource;

/**
 * 自定义伤害来源
 * <p>
 * 定义模组中使用的各种特殊伤害类型
 * </p>
 */
public class NewDamageSource {

    /**
     * 癫火伤害
     * <p>
     * 特性：无视护甲
     * </p>
     */
    public static final DamageSource EPILEPSY_FIRE = new DamageSource("unendurableFrenzy")
            .setDamageBypassesArmor();

    /**
     * 冻伤伤害
     * <p>
     * 特性：普通伤害
     * </p>
     */
    public static final DamageSource FROSTBITE = new DamageSource("frostbite");

    /**
     * 猩红腐烂伤害
     * <p>
     * 特性：魔法伤害，无视护甲
     * </p>
     */
    public static final DamageSource SCARLET_ROT = new DamageSource("scarletRot")
            .setMagicDamage()
            .setDamageBypassesArmor();

    /**
     * 波石魔法伤害
     * <p>
     * 特性：普通伤害
     * </p>
     */
    public static final DamageSource WAVE_STONE_MAGIC = new DamageSource("wave_stone_magic");

    /**
     * 出血伤害
     * <p>
     * 特性：无视护甲
     * </p>
     */
    public static final DamageSource HEMORRHAGE = new DamageSource("hemorrhage")
            .setDamageBypassesArmor();

    // 私有构造函数，防止实例化
    private NewDamageSource() {
    }
}