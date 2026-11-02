package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.UUID;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>神圣之刃附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        }
)
public class EnchantmentSacredBlade extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sacred_blade";

    /**
     * 对亡灵每级的额外伤害倍率
     * <p>默认 0.25，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.25D, 0.0D, 5.0D);

    /**
     * 额外伤害转化为治疗的比例
     * <p>默认 0.2，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle LIFESTEAL_RATIO =
            EnchantmentValues.define(VALUE_ID, "lifesteal_ratio",
                    0.2D, 0.0D, 1.0D);

    /**
     * 单次治疗量的最大生命占比上限
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle LIFESTEAL_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "lifesteal_cap_ratio",
                    0.1D, 0.0D, 1.0D);

    /**
     * 攻击力削弱修正器的 UUID。
     * <p>
     * <b>v3.0 起施加在「被砍的亡灵」身上</b>（此前错误地施加在持有者身上，详见类注释）。
     * UUID 本身保持不变——这样 {@link #onPlayerRespawn} / {@link #onEntityJoinLevel}
     * 的清除逻辑能同时清掉旧版本残留在玩家身上的那份，玩家重登一次即可恢复。
     * </p>
     */
    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");

    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";

    /** 单次削弱步长系数（× 等级）：每命中一次亡灵，其攻击力再降 5%×等级 */
    private static final double PENALTY_STEP_PER_LEVEL = -0.05;

    /** 削弱下限：最多把目标的攻击力削到只剩 1% */
    private static final double PENALTY_FLOOR = -0.99;

    public EnchantmentSacredBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
