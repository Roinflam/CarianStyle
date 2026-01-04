package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

/**
 * 火焰赋予力量附魔
 *
 * 武器附魔，火焰增益效果
 * 攻击时：
 * - 如果自己着火：伤害增加 7.5% × 等级，且续燃10秒（如果燃烧时间<200tick）
 * - 如果自己没着火：点燃自己10秒（创造模式免疫）
 * 受击时：
 * - 如果自己着火且伤害是物理伤害：减伤 3.75% × 等级
 */
@AutoRegisterEnchantment(
        id = "fire_gives_power",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON
)
public class EnchantmentFireGivesPower extends EnchantmentBase {

    public EnchantmentFireGivesPower() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时：着火则增伤并续燃，未着火则点燃自己
     */
    @Override
    protected void onDamageAsAttackerLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();

        if (EntityUtil.getFire(attacker) > 0) {
            // 攻击者着火时：续燃并增伤
            if (EntityUtil.getFire(attacker) < 200) {
                attacker.setFire(10);
            }
            // 伤害增加 7.5% × 等级
            float bonusDamage = ctx.getDamage() * level * 0.075f;
            ctx.addDamage(bonusDamage);
        } else {
            // 攻击者没着火：点燃自己（创造模式免疫）
            if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                attacker.setFire(10);
            }
        }
    }

    /**
     * 受击时：着火则减伤（仅对物理伤害有效）
     */
    @Override
    protected void onDamageAsVictimLow(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase victim = ctx.getHolder();

        // 必须着火
        if (EntityUtil.getFire(victim) <= 0) {
            return;
        }

        // 排除创造模式伤害和魔法伤害
        if (ctx.canHarmInCreative() || ctx.isMagicDamage()) {
            return;
        }

        // 减伤 3.75% × 等级
        float reduction = ctx.getDamage() * level * 0.0375f;
        ctx.reduceDamage(reduction);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}