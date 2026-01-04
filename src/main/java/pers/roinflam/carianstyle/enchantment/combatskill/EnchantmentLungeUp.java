package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.context.EnchantmentContext;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

/**
 * 突刺附魔
 *
 * 疾跑攻击时触发：停止疾跑、短暂缓慢、5tick后击飞敌人、增伤+15%×等级
 */
@AutoRegisterEnchantment(
        id = "lunge_up",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentLungeUp extends EnchantmentBase {

    public EnchantmentLungeUp() {
        super(EnumEnchantmentType.WEAPON, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttacker(@Nonnull EnchantmentContext ctx, int level) {
        EntityLivingBase attacker = ctx.getHolder();
        EntityLivingBase victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        // 必须疾跑中
        if (!attacker.isSprinting()) {
            return;
        }

        // 停止疾跑
        attacker.setSprinting(false);

        // 施加缓慢效果
        attacker.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 10, 6));

        // 5tick后击飞敌人
        new SynchronizationTask(5) {
            @Override
            public void run() {
                if (!attacker.isEntityAlive()) {
                    return;
                }

                LivingKnockBackEvent knockBackEvent = ForgeHooks.onLivingKnockBack(
                        victim, attacker, level * 0.3f, 0, 0);

                if (!knockBackEvent.isCanceled()) {
                    victim.motionY = knockBackEvent.getStrength();
                }
            }
        }.start();

        // 增伤 +15% × 等级
        ctx.addDamage(ctx.getDamage() * level * 0.15f);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }
}