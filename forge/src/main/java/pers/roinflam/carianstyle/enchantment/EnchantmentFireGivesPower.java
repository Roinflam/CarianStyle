package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;

/**
 * 火焰赋予力量附魔
 * <p>
 * 武器附魔，火焰增益效果
 * 攻击时：
 * - 如果自己着火：伤害增加 7.5% × 等级，且续燃10秒（如果燃烧时间<200tick）
 * - 如果自己没着火：点燃自己10秒（创造模式免疫）
 * 受击时：
 * - 如果自己着火且伤害是物理伤害：减伤 3.75% × 等级
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "fire_gives_power",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentFireGivesPower extends EnchantmentBase {

    public EnchantmentFireGivesPower() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();

        if (attacker.getRemainingFireTicks() > 0) {
            if (attacker.getRemainingFireTicks() < 200) {
                attacker.setSecondsOnFire(10);
            }
            float bonusDamage = ctx.getDamage() * level * 0.075f;
            ctx.addDamage(bonusDamage);
        } else {
            if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
                attacker.setSecondsOnFire(10);
            }
        }
    }

    @Override
    protected void onDamageAsVictimLow(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity victim = ctx.getHolder();

        if (victim.getRemainingFireTicks() <= 0) {
            return;
        }

        if (ctx.canHarmInCreative() || ctx.isMagicDamage()) {
            return;
        }

        float reduction = ctx.getDamage() * level * 0.0375f;
        ctx.reduceDamage(reduction);
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