package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.api.IEffectModifier;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 野兽强健附魔
 * <p>
 * 胸甲附魔，缩短药水效果时间但大幅增强等级
 * 获得药水效果时：
 * - 持续时间变为 40%（不受附魔等级影响）
 * - 效果等级变为 原等级 × 2 + 1（不受附魔等级影响），最高不超过 100
 * </p>
 * <p>
 * 1.12.2 版：效果修改由 {@code mc112.hook.EffectModifierHook} 在 {@code PotionAddedEvent} 里调 {@link #modifyEffect}；
 * 1.12.2 没有「无限时长」的效果（时长 -1 当场就过期），不用像 1.20.1 那样单独跳过。
 * </p>
 * <p>
 * 1.12.2 版保留：粒子不可见的效果（{@code doesShowParticles()} 为 false）照样处理；
 * 1.20.1 会跳过 {@code !isVisible()} 的效果。
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "beast_robust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastRobust extends EnchantmentBase implements IEffectModifier {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.beast_robust.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "beast_robust";

    /**
     * 转化后效果等级的上限
     * <p>默认 100，允许范围 0 ~ 127。</p>
     */
    private static final EnchantmentValues.Handle MAX_AMPLIFIER =
            EnchantmentValues.define(VALUE_ID, "max_amplifier",
                    100, 0, 127);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 转化后效果时长相对原时长的比例
     * <p>默认 0.4，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_RATIO =
            EnchantmentValues.define(VALUE_ID, "duration_ratio",
                    0.4D, 0.0D, 5.0D);

    /**
     * 转化后效果等级的倍数（结果再 +1）
     * <p>默认 2，允许范围 1 ~ 20。</p>
     */
    private static final EnchantmentValues.Handle AMPLIFIER_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "amplifier_multiplier",
                    2, 1, 20);

    /**
     * 药水效果等级的最大上限
     */
    public EnchantmentBeastRobust() {
        super(EnumEnchantmentType.ARMOR_CHEST, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    @Override
    public int getEnchantmentLevel(@Nonnull EntityLivingBase entity) {
        // 只从胸甲获取附魔等级
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(this, chest);

        // 应用等级限制
        level = applyLevelLimit(this, level, LEVEL_CAP.getInt());

        return level;
    }

    @Nullable
    @Override
    public PotionEffect modifyEffect(@Nonnull EntityLivingBase entity,
                                     @Nonnull PotionEffect effectInstance,
                                     int enchantmentLevel) {
        // 只要有附魔就生效（等级不影响效果强度）
        if (enchantmentLevel <= 0) {
            return null;
        }

        Potion effect = effectInstance.getPotion();

        // 只对非瞬时的效果生效
        // 1.12.2 版保留：不看粒子是否可见（1.20.1 还要求 isVisible()）
        if (effect.isInstant()) {
            return null;
        }

        // 计算新属性
        int newDuration = (int) (effectInstance.getDuration() * DURATION_RATIO.get());  // 时间缩短到 40%
        int newAmplifier = Math.min(effectInstance.getAmplifier() * AMPLIFIER_MULTIPLIER.getInt() + 1, MAX_AMPLIFIER.getInt());  // 等级翻倍+1，上限100

        // 创建修改后的效果实例（1.12.2 的 PotionEffect 没有单独的图标开关）
        return new PotionEffect(
                effect,
                newDuration,
                newAmplifier,
                effectInstance.getIsAmbient(),
                effectInstance.doesShowParticles()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
