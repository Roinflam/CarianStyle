package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E3)
 * <p>催眠烟雾附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "hypnotic_smoke",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.WEAPON,
        slots = {EntityEquipmentSlot.MAINHAND}
)
public class EnchantmentHypnoticSmoke extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "hypnotic_smoke";

    /**
     * 每级的触发概率（百分比）
     * <p>默认 2.0，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.0D, 0.0D, 100.0D);

    /**
     * 触发到施加效果的延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle APPLY_DELAY =
            EnchantmentValues.define(VALUE_ID, "apply_delay",
                    5, 0, 200);

    /**
     * 每级的催眠持续秒数
     * <p>默认 3，允许范围 1 ~ 120。</p>
     */
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    3, 1, 120);

    public EnchantmentHypnoticSmoke() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEatShit.class));
    }
}
