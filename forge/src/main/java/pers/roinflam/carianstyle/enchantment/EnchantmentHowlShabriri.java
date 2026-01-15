package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 沙布里里嚎叫附魔
 *
 * 武器附魔，叠层诅咒系统
 * 攻击时：
 * - 给目标叠加沙布里里嚎叫效果（持续 = 等级 × 3秒，层数+1，最高5层）
 * - 目标满5层时，伤害增加 15% × 等级
 * - 攻击者自身受到癫火伤害（3秒内共5%最大生命值，创造模式免疫）
 */
@AutoRegisterEnchantment(
        id = "howl_shabriri",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentHowlShabriri extends EnchantmentBase {

    public EnchantmentHowlShabriri() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时叠加诅咒并自伤
     */
    @Override
    protected void onDamageAsAttackerLowest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 玩家必须是刚挥动武器
        if (attacker instanceof EntityPlayer) {
            if (!isJustSwung((EntityPlayer) attacker)) {
                return;
            }
        }

        // 获取当前层数
        int currentAmplifier = 0;
        if (victim.isPotionActive(CarianStylePotion.HOWL_SHABRIRI)) {
            currentAmplifier = victim.getActivePotionEffect(CarianStylePotion.HOWL_SHABRIRI).getAmplifier();
        }

        // 满5层时增伤
        if (currentAmplifier >= 5) {
            float bonusDamage = ctx.getDamage() * level * 0.15f;
            ctx.addDamage(bonusDamage);
        }

        // 叠加沙布里里嚎叫效果（层数+1，最高5层）
        victim.addPotionEffect(new PotionEffect(
                CarianStylePotion.HOWL_SHABRIRI,
                level * 3 * 20,
                Math.min(currentAmplifier + 1, 5)
        ));

        // 攻击者受到癫火伤害（创造模式免疫）
        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.EPILEPSY_FIRE_BURNING,
                    3 * 20 + 5,
                    0
            ));

            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > 60 || !attacker.isEntityAlive()) {
                        this.cancel();
                        return;
                    }

                    // 每tick造成 5%/60 最大生命值伤害
                    float damage = attacker.getMaxHealth() * 0.05f / 60;
                    if (attacker.getHealth() - damage * 2 > 0) {
                        attacker.setHealth(attacker.getHealth() - damage);
                    } else {
                        EntityLivingUtil.kill(attacker, NewDamageSource.EPILEPSY_FIRE);
                        this.cancel();
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}