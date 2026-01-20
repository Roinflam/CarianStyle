package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 死亡之刃附魔
 * <p>
 * 武器附魔
 * 初始伤害降为50%，但施加死亡烙印
 * 持续5秒造成累计伤害（总伤害=原伤害×75%）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "death_blade",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentDeathBlade extends EnchantmentBase {

    public EnchantmentDeathBlade() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        DamageSource damageSource = ctx.getDamageSource();

        if (damageSource == null || "deathBlade".equals(damageSource.getMsgId()) ||
                damageSource.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        float damage = ctx.getDamage() * 0.75f / 100;

        ctx.multiplyDamage(0.5f);

        // 应用火焰燃烧效果（需要同步网络）
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH_BURNING.createInstance(5 * 20 + 5, 0));
        ClientSyncEffectHelper.onAttributeApplied(ctx.getVictim(), DynamicAttributes.DOOMED_DEATH_BURNING);

        // 应用注定死亡效果
        DynamicAttributeManager.apply(ctx.getVictim(),
                DynamicAttributes.DOOMED_DEATH.createInstance(10 * 20 + 5, 0));

        new SynchronizationTask(1, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > 100 || !ctx.getVictim().isAlive()) {
                    this.cancel();
                    return;
                }

                if (ctx.getVictim().getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(ctx.getVictim(), damage);
                } else {
                    EntityLivingUtil.kill(ctx.getVictim(), damageSource);
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (50 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}