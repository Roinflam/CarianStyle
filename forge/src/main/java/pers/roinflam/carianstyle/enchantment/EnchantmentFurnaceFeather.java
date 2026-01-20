package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;

/**
 * 熔炉之羽附魔
 * <p>
 * 护甲附魔，以受到更多伤害换取机动性增益
 * 受击时：
 * - 受到的伤害增加50% * 附魔等级
 * - 大幅增加无敌帧（invulnerableTime = max + max/2 × 等级 × 1.5）
 * - 获得速度效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * - 获得跳跃提升效果（持续 = 等级 × 40tick，效果等级 = 附魔等级 - 1）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "furnace_feather",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
public class EnchantmentFurnaceFeather extends EnchantmentBase {

    public EnchantmentFurnaceFeather() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 受击后获得增益效果（最低优先级）
     * 注意：这里使用受害者视角的 onDamageAsVictimLowest
     */
    @Override
    protected void onDamageAsVictimLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        // 手动应用等级限制（虽然 EnchantmentBase 已经限制过了，但为了保险再限制一次）
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 增加无敌帧
        victim.invulnerableTime = (int) (victim.invulnerableDuration +
                victim.invulnerableDuration / 2.0 * effectiveLevel * 1.5);

        // 添加速度效果
        victim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                effectiveLevel * 40,
                effectiveLevel - 1
        ));

        // 添加跳跃提升效果
        victim.addEffect(new MobEffectInstance(
                MobEffects.JUMP,
                effectiveLevel * 40,
                effectiveLevel - 1
        ));

        ctx.multiplyDamage(1 + 0.5f * level);
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
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}