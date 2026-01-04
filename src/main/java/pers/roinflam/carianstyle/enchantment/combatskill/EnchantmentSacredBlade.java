package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.EnumCreatureAttribute;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import javax.annotation.Nonnull;
import java.util.UUID;

/**
 * 神圣之刃附魔
 *
 * 对亡灵生物：额外伤害 = 伤害 × 等级 × 0.25 × 目标血量比例，并治疗自身
 * 但会累积降低自身攻击力（等级 × -5%，最多-99%）
 * 对非亡灵生物：伤害降为20%
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        conflictsWith = {
                EnchantmentScarletCorruption.class,
                EnchantmentDeathBlade.class,
                EnchantmentBlackFlameBlade.class
        }
)
public class EnchantmentSacredBlade extends EnchantmentBase {

    private static final UUID ATTACK_DAMAGE_MODIFIER_ID = UUID.fromString("0dada439-4e61-fd5e-44d7-c620fd5a11fb");
    private static final String ATTACK_DAMAGE_MODIFIER_NAME = "enchantment.sacred_blade";

    public EnchantmentSacredBlade() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 判断是否为亡灵生物
        if (victim.getCreatureAttribute() == EnumCreatureAttribute.UNDEAD) {
            // 对亡灵：额外伤害 = 当前伤害 × 等级 × 0.25 × 目标血量比例
            float healthRatio = victim.getHealth() / victim.getMaxHealth();
            float bonusDamage = ctx.getDamage() * level * 0.25f * healthRatio;
            ctx.addDamage(bonusDamage);

            // 治疗攻击者（上限为最大血量的10%）
            float healAmount = Math.min(bonusDamage * 0.2f, attacker.getMaxHealth() * 0.1f);
            attacker.heal(healAmount);

            // 累积攻击力惩罚
            applyAttackPenalty(attacker, level);
        } else {
            // 对非亡灵：伤害降为20%
            ctx.multiplyDamage(0.2f);
        }
    }

    /**
     * 应用攻击力惩罚（累积降低，最多-99%）
     */
    private void applyAttackPenalty(@Nonnull EntityLivingBase attacker, int level) {
        IAttributeInstance attributeInstance = attacker.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (attributeInstance == null) {
            return;
        }

        double newReduction = Math.max(level * -0.05, -0.99);
        AttributeModifier existing = attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID);

        if (existing == null) {
            attributeInstance.applyModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    2
            ));
        } else if (existing.getAmount() > newReduction) {
            // 累积更大的惩罚
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
            attributeInstance.applyModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    2
            ));
        }
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        // 与原版锋利冲突
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.canApplyTogether(ench);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}