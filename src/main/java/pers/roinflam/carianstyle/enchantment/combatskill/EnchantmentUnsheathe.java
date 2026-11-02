package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import java.util.UUID;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E1)
 * <p>居合附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "unsheathe",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentUnsheathe extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "unsheathe";

    /**
     * 居合基础触发概率（百分比）
     * <p>默认 1.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_TRIGGER_CHANCE =
            EnchantmentValues.define(VALUE_ID, "base_trigger_chance",
                    1.0D, 0.0D, 100.0D);

    /**
     * 每累积一次攻击增加的触发概率（百分比）
     * <p>默认 0.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_ATTACK =
            EnchantmentValues.define(VALUE_ID, "chance_per_attack",
                    0.5D, 0.0D, 10.0D);

    /**
     * 触发时每级的伤害倍率
     * <p>默认 3.3，允许范围 0.0 ~ 50.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    3.3D, 0.0D, 50.0D);

    private static final UUID ATTACK_SPEED_MODIFIER_ID = UUID.fromString("a9f7b1c6-4c2d-4f0e-9f2c-3a8b3f7d0a5b");

    private static final String ATTACK_SPEED_MODIFIER_NAME = "enchantment.unsheathe";

    private static final String ATTACK_COUNT_KEY = "unsheathe_attack_count";

    public EnchantmentUnsheathe() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((28 + (enchantmentLevel - 1) * 8) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
