package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 奉剑附魔
 * <p>
 * 满血时攻击增伤 +10% × 等级
 * </p>
 * <h3>视觉反馈由 HUD 承担，不做世界特效</h3>
 * <p>
 * 这个附魔的全部价值就在「满血」这一个开关上，掉一滴血就失效。
 * 而<b>开关状态是持续的，不是瞬间的</b>——玩家真正需要的是随时能瞥一眼
 * 「我现在还满血吗、加成还在吗」，而不是攻击那一瞬间闪一下。
 * 因此改由 {@code CarianStyleCombatStateDisplay} 在 HUD 上显示：
 * 那一行出现即代表加成生效，掉血立刻消失。
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "offer_sword",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentOfferSword extends EnchantmentBase {

    public EnchantmentOfferSword() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        // 满血时才触发
        if (attacker.getHealth() < attacker.getMaxHealth()) {
            return;
        }

        // 增伤 +10% × 等级
        ctx.addDamage(ctx.getDamage() * level * 0.1f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
