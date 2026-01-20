package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;

/**
 * 注定死亡附魔
 * <p>
 * 攻击时施加诅咒效果，并造成持续递增伤害
 * 持续100tick，伤害随时间递增，足以致死时直接击杀
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "doomed_death",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentDoomedDeath extends EnchantmentBase {

    public EnchantmentDoomedDeath() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null || victim.level().isClientSide) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 玩家需要刚挥剑
        if (ctx.isHolderPlayer() && !isJustSwung(ctx.getHolderAsPlayer())) {
            return;
        }

        // 应用注定死亡燃烧效果（5秒 + 5tick，会自动同步客户端渲染猩红色火焰）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(5 * 20 + 5, 0)
        );

        // 应用注定死亡效果（10秒 + 5tick，最大生命值-25%）
        DynamicAttributeManager.apply(
                victim,
                DynamicAttributes.DOOMED_DEATH.createInstance(10 * 20 + 5, 0)
        );

        // 持续伤害任务
        float originalDamage = ctx.getDamage();
        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || victim.isDeadOrDying()) {
                    this.cancel();
                    return;
                }

                float baseDamage = (originalDamage * 0.5f + victim.getHealth() * 0.1f) / 100;
                float actualDamage = baseDamage * 0.3f + baseDamage * tick / 50 * 0.7f;

                if (victim.getHealth() - actualDamage > 0.01f) {
                    victim.setHealth(victim.getHealth());
                    EntityLivingUtil.damageHealthDirectly(victim, actualDamage);
                } else {
                    EntityLivingUtil.kill(victim, ctx.getDamageSource());
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}