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
     * 特性：魔法伤害（女巫抗性、魔法减伤类附魔按魔法算），无视护甲；绝对伤害（不吃抗性提升和保护类附魔），
     * 否则抗性提升 V 会把致死那一下减成 0，自损和对敌的癫火都烧不死人
     * </p>
     */
    public static final DamageSource EPILEPSY_FIRE = new DamageSource("unendurableFrenzy")
            .setMagicDamage()
            .setDamageBypassesArmor()
            .setDamageIsAbsolute();

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
     * 特性：魔法伤害，无视护甲和抗性提升，保护类附魔仍有效。
     * 1.12.2 没有「只跳过抗性」的开关：这里设成绝对伤害（抗性和保护附魔都跳过），
     * 保护附魔的减免由造成伤害的一方先手算好（见 {@code MobEffectScarletRot}）
     * </p>
     */
    public static final DamageSource SCARLET_ROT = new DamageSource("scarletRot")
            .setMagicDamage()
            .setDamageBypassesArmor()
            .setDamageIsAbsolute();

    /**
     * 波石魔法伤害
     * <p>
     * 特性：魔法伤害
     * </p>
     */
    public static final DamageSource WAVE_STONE_MAGIC = new DamageSource("wave_stone_magic")
            .setMagicDamage();

    /**
     * 出血伤害
     * <p>
     * 特性：无视护甲；绝对伤害（不吃抗性提升和保护类附魔）。
     * 出血 / 切腹的致死一击走伤害管线（不死图腾挡得住），不设绝对伤害的话抗性提升 V 会把它减成 0，
     * 目标停在快死的血量上永远死不了
     * </p>
     */
    public static final DamageSource HEMORRHAGE = new DamageSource("hemorrhage")
            .setDamageBypassesArmor()
            .setDamageIsAbsolute();

    // 私有构造函数，防止实例化
    private NewDamageSource() {
    }
}