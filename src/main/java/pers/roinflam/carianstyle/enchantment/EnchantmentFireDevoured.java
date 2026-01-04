package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 火焰吞噬附魔
 *
 * 攻击者着火时，对目标周围敌人造成火焰伤害并点燃
 */
@AutoRegisterEnchantment(
        id = "fire_devoured",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentFireDevoured extends EnchantmentBase {

    public EnchantmentFireDevoured() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onAttackLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 攻击者必须着火
        if (EntityUtil.getFire(attacker) <= 0) {
            return;
        }

        // 获取目标周围实体（范围=等级）
        // 注意：原代码过滤条件会包含攻击者，保持原逻辑
        List<EntityLivingBase> nearbyEntities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                effectiveLevel,
                entity -> !entity.equals(victim) || entity.equals(attacker)
        );

        float damage = ctx.getDamage() * effectiveLevel * 0.15f;

        for (EntityLivingBase entity : nearbyEntities) {
            // 造成火焰伤害
            entity.attackEntityFrom(DamageSource.IN_FIRE, damage);

            // 如果燃烧时间<200tick，点燃10秒
            if (EntityUtil.getFire(entity) < 200) {
                entity.setFire(10);
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}