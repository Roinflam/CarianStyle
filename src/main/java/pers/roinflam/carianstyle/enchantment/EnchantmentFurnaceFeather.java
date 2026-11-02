package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import javax.annotation.Nonnull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 熔炉之羽附魔
 * <p>
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时：
 * - 受到的伤害增加50% * 全身等级之和（只算一次，四件 Ⅲ 级 = 12 级，×7）
 * - 大幅增加无敌帧（hurtResistantTime = max + max/2 × 等级 × 1.5）
 * - 获得速度效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * - 获得跳跃提升效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * 后三项按等级最高的一件算，不叠加
 * </p>
 * <p>
 * 分发器按件回调，以前每件各乘一次，四件 Ⅲ 级约 ×39。现在只在第一件上结算一次。
 * </p>
 * <p>
 * 1.12.2 版：1.20.1 的 {@code invulnerableTime} / {@code invulnerableDuration} 是 {@code hurtResistantTime} /
 * {@code maxHurtResistantTime}；原版在发 {@code LivingDamageEvent} 之前就把 {@code hurtResistantTime} 设成了上限，
 * 这里在事件里直接覆盖掉。
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.ARMOR,
        slots = {EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS, EntityEquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentFurnaceFeather extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.furnace_feather.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "furnace_feather";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 每级延长无敌帧的系数
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle INVULNERABLE_FACTOR =
            EnchantmentValues.define(VALUE_ID, "invulnerable_factor",
                    1.5D, 0.0D, 10.0D);

    /**
     * 每级施加效果的持续时间（tick）
     * <p>默认 40，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle EFFECT_TICKS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "effect_ticks_per_level",
                    40, 1, 1200);

    /**
     * 每级的伤害倍率加成
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_per_level",
                    0.5D, 0.0D, 5.0D);

    public EnchantmentFurnaceFeather() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 受击后获得增益效果（最低优先级）
     * 注意：这里使用受害者视角的 onDamageAsVictimLowest
     */
    @Override
    protected void onDamageAsVictimLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        // 分发器每件回调一次，全身一起结算，只认扫到的第一件
        int totalLevel = 0;
        int highestLevel = 0;
        ItemStack firstPiece = null;
        for (EntityEquipmentSlot slot : EntityEquipmentSlot.values()) {
            ItemStack stack = victim.getItemStackFromSlot(slot);
            int pieceLevel = applyLevelLimit(EnchantmentHelper.getEnchantmentLevel(this, stack));
            if (pieceLevel > 0) {
                if (firstPiece == null) {
                    firstPiece = stack;
                }
                totalLevel += pieceLevel;
                highestLevel = Math.max(highestLevel, pieceLevel);
            }
        }
        if (ctx.getEnchantedItem() != firstPiece) {
            return;
        }

        // 手动应用等级限制（虽然 EnchantmentBase 已经限制过了，但为了保险再限制一次）
        int effectiveLevel = capLevel(highestLevel, LEVEL_CAP.getInt());

        // 增加无敌帧
        victim.hurtResistantTime = (int) (victim.maxHurtResistantTime +
                victim.maxHurtResistantTime / 2.0 * effectiveLevel * INVULNERABLE_FACTOR.get());

        // 添加速度效果
        victim.addPotionEffect(new PotionEffect(
                MobEffects.SPEED,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL.getInt(),
                effectiveLevel - 1
        ));

        // 添加跳跃提升效果
        victim.addPotionEffect(new PotionEffect(
                MobEffects.JUMP_BOOST,
                effectiveLevel * EFFECT_TICKS_PER_LEVEL.getInt(),
                effectiveLevel - 1
        ));

        ctx.multiplyDamage(1 + (float) DAMAGE_PER_LEVEL.get() * totalLevel);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}
