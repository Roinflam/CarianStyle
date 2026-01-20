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
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 沙布里里嚎叫附魔
 * <p>
 * 武器附魔，叠层诅咒系统
 * 攻击时：
 * - 给目标叠加沙布里里嚎叫效果（持续 = 等级 × 3秒，层数+1，最高5层）
 * - 目标满5层时，伤害增加 15% × 等级
 * - 攻击者自身受到癫火伤害（3秒内共5%最大生命值，创造模式免疫）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "howl_shabriri",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
public class EnchantmentHowlShabriri extends EnchantmentBase {

    public EnchantmentHowlShabriri() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();

        if (victim == null) {
            return;
        }

        if (attacker instanceof Player) {
            if (((Player) attacker).getAttackStrengthScale(0.5F) < 0.9F) {
                return;
            }
        }

        // 获取当前沙布里里嚎叫等级
        int currentAmplifier = DynamicAttributeManager.getAmplifier(victim, DynamicAttributes.HOWL_SHABRIRI);

        if (currentAmplifier >= 5) {
            // 满5层，增加伤害
            float bonusDamage = ctx.getDamage() * level * 0.15f;
            ctx.addDamage(bonusDamage);
        }

        // 叠加沙布里里嚎叫效果（最高5层，amplifier=5）
        int newAmplifier = currentAmplifier < 0 ? 0 : Math.min(currentAmplifier + 1, 5);
        DynamicAttributeManager.apply(victim,
                DynamicAttributes.HOWL_SHABRIRI.createInstance(level * 3 * 20, newAmplifier));

        // 对攻击者造成癫火伤害（创造模式玩家免疫）
        if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
            // 应用火焰燃烧效果（需要同步网络）
            DynamicAttributeManager.apply(attacker,
                    DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(3 * 20 + 5, 0));
            ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > 60 || !attacker.isAlive()) {
                        this.cancel();
                        return;
                    }

                    float damage = attacker.getMaxHealth() * 0.05f / 60;
                    if (attacker.getHealth() - damage * 2 > 0) {
                        EntityLivingUtil.damageHealthDirectly(attacker, damage);
                    } else {
                        EntityLivingUtil.kill(attacker, NewDamageSource.epilepsyFire(attacker.level()));
                        this.cancel();
                    }
                }
            }.start();
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}