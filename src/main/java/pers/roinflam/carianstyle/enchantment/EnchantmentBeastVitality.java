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
 * 野兽活力附魔
 * <p>
 * 胸甲附魔，延长药水效果持续时间
 * 获得药水效果时：持续时间增加 30% × 等级
 * </p>
 * <p>
 * 1.12.2 版：效果修改由 {@code mc112.hook.EffectModifierHook} 在 {@code PotionAddedEvent} 里调 {@link #modifyEffect}；
 * 1.12.2 没有「无限时长」的效果，不用像 1.20.1 那样单独跳过。1.12.2 的模组常拿一个极大的时长当「永久」用，
 * 延长后可能超出 int 范围变成负数（效果当场消失），所以结果封顶在 {@code Integer.MAX_VALUE}。
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
        id = "beast_vitality",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnumEnchantmentType.ARMOR_CHEST,
        slots = {EntityEquipmentSlot.CHEST},
        forceTreasure = true
)
public class EnchantmentBeastVitality extends EnchantmentBase implements IEffectModifier {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.beast_vitality.desc，
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
        // 没有附魔等级，不处理
        if (enchantmentLevel <= 0) {
            return null;
        }

        Potion effect = effectInstance.getPotion();

        // 只对非瞬时的效果生效
        // 1.12.2 版保留：不看粒子是否可见（1.20.1 还要求 isVisible()）
        if (effect.isInstant()) {
            return null;
        }

        // 计算新的持续时间：原时间 + 原时间 × 等级 × 30%
        int originalDuration = effectInstance.getDuration();
        int addedDuration = (int) (originalDuration * enchantmentLevel * DURATION_PER_LEVEL.get());
        // 1.12.2 版：超长时长相加可能溢出，封顶
        int newDuration = (int) Math.min(Integer.MAX_VALUE, (long) originalDuration + addedDuration);

        // 创建延长时间的效果实例（其他属性不变）
        return new PotionEffect(
                effect,
                newDuration,
                effectInstance.getAmplifier(),
                effectInstance.getIsAmbient(),
                effectInstance.doesShowParticles()
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
