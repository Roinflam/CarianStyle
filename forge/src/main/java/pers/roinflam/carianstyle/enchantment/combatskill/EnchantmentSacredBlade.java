package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBlackFlameBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentDeathBlade;
import pers.roinflam.carianstyle.enchantment.EnchantmentScarletCorruption;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;

import java.util.UUID;

/**
 * 神圣之刃附魔
 * <p>
 * 对亡灵生物：额外伤害 = 伤害 × 等级 × 0.25 × 目标血量比例，并治疗自身
 * 但会累积降低自身攻击力（等级 × -5%，最多-99%）
 * 对非亡灵生物：伤害降为20%
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "sacred_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
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
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 判断是否为亡灵生物
        if (victim.getMobType() == MobType.UNDEAD) {
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
    private void applyAttackPenalty(@NotNull LivingEntity attacker, int level) {
        AttributeInstance attributeInstance = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attributeInstance == null) {
            return;
        }

        double newReduction = Math.max(level * -0.05, -0.99);
        AttributeModifier existing = attributeInstance.getModifier(ATTACK_DAMAGE_MODIFIER_ID);

        if (existing == null) {
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        } else if (existing.getAmount() > newReduction) {
            // 累积更大的惩罚
            attributeInstance.removeModifier(ATTACK_DAMAGE_MODIFIER_ID);
            attributeInstance.addPermanentModifier(new AttributeModifier(
                    ATTACK_DAMAGE_MODIFIER_ID,
                    ATTACK_DAMAGE_MODIFIER_NAME,
                    newReduction,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            ));
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        // 与原版锋利冲突
        if (ench == Enchantments.SHARPNESS) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}