package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

import javax.annotation.Nonnull;

/**
 * 隐形武器附魔
 *
 * 武器/弓箭附魔，攻击后获得隐身
 * 攻击时：
 * - 获得隐身效果（持续 = 等级 × 1 秒）
 * - 箭矢攻击时持续时间 × 3
 * <p>
 * 以前是 等级 × 10 tick（半秒）。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "invisible_weapon",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentInvisibleWeapon extends EnchantmentBase {

    /** 每级隐身时长（tick） */
    private static final int STEALTH_TICKS_PER_LEVEL = 20;
    /** 箭矢命中时额外增加的倍率（总倍率 = 1 + 这个值） */
    private static final int ARROW_BONUS_MAGNIFICATION = 2;

    public EnchantmentInvisibleWeapon() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击后获得隐身效果
     */
    @Override
    protected void onDamageAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        DamageSource damageSource = ctx.getDamageSource();

        // 计算持续时间倍率
        int magnification = 1;
        if (damageSource != null && damageSource.getImmediateSource() instanceof EntityArrow) {
            magnification += ARROW_BONUS_MAGNIFICATION;  // 箭矢攻击倍率 = 3
        }

        // 施加隐身效果
        attacker.addPotionEffect(new PotionEffect(
                CarianStylePotion.STEALTH,
                level * STEALTH_TICKS_PER_LEVEL * magnification
        ));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench)
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentHypnoticArrow.class));
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}