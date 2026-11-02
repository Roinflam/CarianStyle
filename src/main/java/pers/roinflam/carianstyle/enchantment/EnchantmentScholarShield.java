package pers.roinflam.carianstyle.enchantment;

import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * PORT-STUB(E4)
 * <p>学者盾附魔：1.12.2 的桩。只有注册信息、附魔台花费、兼容性和静态常量（含可调数值句柄），
 * 效果还没有，换成实装时照 1.20.1 同名类写。</p>
 */
@Deprecated
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND}
)
public class EnchantmentScholarShield extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "scholar_shield";

    /**
     * 每级反弹给攻击者的伤害比例
     * <p>默认 0.1，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reflect_per_level",
                    0.1D, 0.0D, 5.0D);

    /**
     * 每级的减伤比例
     * <p>默认 0.075，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.075D, 0.0D, 1.0D);

    /**
     * 线程级重入保护标记。
     * <p>
     * 当本线程正在执行学者盾反伤时置为 {@code true}。
     * 反伤使用带攻击者实体的 mobAttack 伤害源，会再次进入 {@link #onLivingAttack}，
     * 若被反伤者也举着学者盾则会再次反伤，形成来回反弹。
     * 此标记在 onLivingAttack 入口拦截，确保反伤只发生一次。
     * 注意：减伤监听器 {@link #onLivingHurt} 不受此标记影响，
     * 被反伤者举盾减免反伤的正常效果完整保留。
     * </p>
     */
    private static final ThreadLocal<Boolean> PROCESSING_RETALIATION = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public EnchantmentScholarShield() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EntityEquipmentSlot[]{
                EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND
        });
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
