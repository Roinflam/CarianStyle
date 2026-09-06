package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
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
 * 清醒附魔
 * <p>
 * 护甲附魔，缩短负面效果持续时间但增强效果
 * 获得负面效果时：
 * - 持续时间减少 15% × 等级
 * - 效果等级+1（效果更强但更短）
 * </p>
 *
 * @author RoinFlam
 * @version 3.0
 */
@AutoRegisterEnchantment(
        id = "lucidity",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentLucidity extends EnchantmentBase implements IEffectModifier {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.lucidity.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "lucidity";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级缩短负面效果时长的比例
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_reduction_per_level",
                    0.15D, 0.0D, 1.0D);

    /**
     * 转化后效果等级的增量
     * <p>默认 1，允许范围 0 ~ 20。</p>
     */
    private static final EnchantmentValues.Handle AMPLIFIER_BONUS =
            EnchantmentValues.define(VALUE_ID, "amplifier_bonus",
                    1, 0, 20);

    public EnchantmentLucidity() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @Override
    public int getEnchantmentLevel(@NotNull LivingEntity entity) {
        // 从所有护甲槽位累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(this, armor);
            }
        }

        // 应用等级限制
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, LEVEL_CAP.getInt());
        }

        return totalLevel;
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

        // 只对非瞬时、可见的负面效果生效
        if (effect.isInstantenous()
                || !effectInstance.isVisible()
                || !effect.getCategory().equals(MobEffectCategory.HARMFUL)) {
            return null;
        }

        // 计算新的持续时间（减少 15% × 等级）
        int originalDuration = effectInstance.getDuration();
        int newDuration = (int) (originalDuration
                * (1.0 - enchantmentLevel * DURATION_REDUCTION_PER_LEVEL.get()));

        // 确保持续时间至少为 1 tick
        newDuration = Math.max(newDuration, 1);

        // 创建修改后的效果实例（持续时间减少，等级+1）
        return new MobEffectInstance(
                effect,
                newDuration,
                effectInstance.getAmplifier() + AMPLIFIER_BONUS.getInt(),  // 等级+1，效果更强
                effectInstance.isAmbient(),
                effectInstance.isVisible(),
                effectInstance.showIcon()
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
