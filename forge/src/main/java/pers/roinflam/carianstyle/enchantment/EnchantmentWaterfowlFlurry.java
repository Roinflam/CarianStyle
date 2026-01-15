package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 水鸟乱舞附魔
 *
 * 武器附魔，连击系统
 * 攻击时：
 * - 将伤害分成(等级+1)段
 * - 重置玩家攻击冷却
 * - 每2tick造成一段伤害
 */
@AutoRegisterEnchantment(
        id = "waterfowl_flurry",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentWaterfowlFlurry extends EnchantmentBase {

    public EnchantmentWaterfowlFlurry() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时触发连击
     */
    @Override
    protected void onHurtAsAttackerHighest(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();
        DamageSource damageSource = ctx.getDamageSource();

        if (victim == null || damageSource == null) {
            return;
        }

        // 玩家必须是刚挥动武器
        if (attacker instanceof EntityPlayer) {
            if (!isJustSwung((EntityPlayer) attacker)) {
                return;
            }
            // 重置攻击冷却
            ((EntityPlayer) attacker).resetCooldown();
        }

        // 防止递归：检查伤害类型
        if (damageSource.damageType.equals("waterfowlDance") || damageSource.damageType.equals("noDeathBlade")) {
            return;
        }

        // 将伤害分成(等级+1)段
        float damagePerHit = ctx.getDamage() / (level + 1);
        ctx.setDamage(damagePerHit);

        // 标记伤害类型防止递归
        damageSource.damageType = "waterfowlDance";

        // 延迟造成剩余伤害
        new SynchronizationTask(1, 2) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > level || !victim.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                // 重置无敌帧
                victim.hurtResistantTime = victim.maxHurtResistantTime / 2;
                victim.attackEntityFrom(damageSource, damagePerHit);
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}