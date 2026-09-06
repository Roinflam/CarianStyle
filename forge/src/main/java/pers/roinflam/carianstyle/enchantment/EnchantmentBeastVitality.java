package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.api.IEffectModifier;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 野兽活力附魔
 * <p>
 * 胸甲附魔，延长药水效果持续时间
 * 获得药水效果时：持续时间增加 30% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "beast_vitality",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastVitality extends EnchantmentBase implements IEffectModifier {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.beast_vitality.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "beast_vitality";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级延长效果时长的比例
     * <p>默认 0.3，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_per_level",
                    0.3D, 0.0D, 5.0D);

    public EnchantmentBeastVitality() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @Override
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        // 只从胸甲获取附魔等级
        ItemStack chest = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (chest.isEmpty()) {
            return 0;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(this, chest);

        // 应用等级限制
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }

        return level;
    }

    @Nullable
    @Override
    public MobEffectInstance modifyEffect(@NotNull LivingEntity entity,
                                          @NotNull MobEffectInstance effectInstance,
                                          int enchantmentLevel) {
        // 没有附魔等级，不处理
        if (enchantmentLevel <= 0) {
            return null;
        }

        MobEffect effect = effectInstance.getEffect();

        // 只对非瞬时、可见的效果生效
        if (effect.isInstantenous() || !effectInstance.isVisible()) {
            return null;
        }

        // 计算新的持续时间：原时间 + 原时间 × 等级 × 30%
        int originalDuration = effectInstance.getDuration();
        int addedDuration = (int) (originalDuration * enchantmentLevel * DURATION_PER_LEVEL.get());
        int newDuration = originalDuration + addedDuration;

        // 创建延长时间的效果实例（其他属性不变）
        return new MobEffectInstance(
                effect,
                newDuration,
                effectInstance.getAmplifier(),
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
