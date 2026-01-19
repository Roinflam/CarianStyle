package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
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
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

/**
 * 水鸟乱舞附魔
 * <p>
 * 武器附魔，连击系统
 * 攻击时：
 * - 将伤害分成(等级+1)段
 * - 重置玩家攻击冷却
 * - 每2tick造成一段伤害
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "waterfowl_flurry",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        forceTreasure = true
)
public class EnchantmentWaterfowlFlurry extends EnchantmentBase {

    private static final String DAMAGE_TYPE_MARKER = "waterfowl_dance_marker";

    public EnchantmentWaterfowlFlurry() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @Override
    protected void onHurtAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
        LivingEntity attacker = ctx.getHolder();
        LivingEntity victim = ctx.getVictim();
        DamageSource damageSource = ctx.getDamageSource();

        if (victim == null || damageSource == null) {
            return;
        }

        if (attacker instanceof Player) {
            if (!isJustSwung((Player) attacker)) {
                return;
            }
            ((Player) attacker).resetAttackStrengthTicker();
        }

        // 防止递归：使用DataManager检查
        if (EnchantmentDataManager.getData(DAMAGE_TYPE_MARKER, attacker.getUUID()) != null) {
            return;
        }

        // 防止与死亡之刃冲突
        if ("deathBlade".equals(damageSource.getMsgId()) || "noDeathBlade".equals(damageSource.getMsgId())) {
            return;
        }

        float damagePerHit = ctx.getDamage() / (level + 1);
        ctx.setDamage(damagePerHit);

        // 标记为水鸟乱舞伤害
        EnchantmentDataManager.setData(DAMAGE_TYPE_MARKER, attacker.getUUID(), true, 100);

        new SynchronizationTask(1, 2) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > level || !victim.isAlive()) {
                    EnchantmentDataManager.removeData(DAMAGE_TYPE_MARKER, attacker.getUUID());
                    this.cancel();
                    return;
                }

                victim.invulnerableTime = 10;
                victim.hurt(damageSource, damagePerHit);
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((30 + (enchantmentLevel - 1) * 30) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}