package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
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
 * 满蓄力疾跑攻击时触发：停止疾跑、短暂缓慢、5tick后击飞敌人、增伤+15%×等级
 * <p>
 * 要求满蓄力：武器不带击退附魔时，低蓄力命中不会打断疾跑，以前疾跑中连点能把一群怪挨个挑飞。
 * 击飞改为直接设竖直速度并标记速度同步，受击者是玩家时客户端也收得到；
 * 以前走击退事件改速度、不同步，玩家基本挑不起来，别的模组还能把它取消。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "lunge_up",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
public class EnchantmentLungeUp extends EnchantmentBase {

    // 每级击飞的竖直速度
    private static final double KNOCKUP_PER_LEVEL = 0.3D;
    // 每级增伤比例
    private static final float DAMAGE_PER_LEVEL = 0.15F;
    // 击飞延迟（tick）
    private static final int KNOCKUP_DELAY_TICKS = 5;

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

        // 满蓄力
        if (!isFullyCharged(attacker)) {
            return;
        }

        // 停止疾跑
        attacker.setSprinting(false);

        // 施加缓慢效果
        attacker.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 10, 6));

        // 5tick后击飞敌人
        new SynchronizationTask(KNOCKUP_DELAY_TICKS) {
            @Override
            public void run() {
                if (!attacker.isEntityAlive() || !victim.isEntityAlive()) {
                    return;
                }

                victim.motionY = level * KNOCKUP_PER_LEVEL;
                victim.velocityChanged = true;
            }
        }.start();

        // 增伤 +15% × 等级
        ctx.addDamage(ctx.getDamage() * level * DAMAGE_PER_LEVEL);
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }
}