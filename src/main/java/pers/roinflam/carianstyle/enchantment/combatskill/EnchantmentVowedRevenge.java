package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@AutoRegisterEnchantment(
        id = "vowed_revenge",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentVowedRevenge extends EnchantmentBase {

    public EnchantmentVowedRevenge() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getAttacker();
        EntityLivingBase victim = ctx.getVictim();

        List<Entity> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                attacker,
                level * 2,
                entityLivingBase -> !entityLivingBase.equals(attacker)
        );

        float damageIncrease = ctx.getDamage() * level * entities.size() * 0.025f;
        ctx.addDamage(damageIncrease);

        if (attacker.getRevengeTarget() != null && attacker.getRevengeTarget().equals(victim)) {
            float revengeDamage = ctx.getDamage() * level * 0.05f;
            ctx.addDamage(revengeDamage);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}