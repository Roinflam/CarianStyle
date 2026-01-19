package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

/**
 * 空癫火附魔
 * <p>
 * 弓箭附魔，双刃剑效果
 * 箭矢命中敌人时：
 * - 对敌人造成持续3秒的癫火伤害（攻击者最大生命值 × 15% × 等级）
 * - 对自己造成持续3秒的癫火伤害（自己最大生命值 × 15%）
 * - 创造模式玩家免疫自损
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {

    private static final int BURN_DURATION = 65;  // 3秒 + 5 tick
    private static final int DAMAGE_TICKS = 60;   // 持续伤害60 tick (3秒)

    public EnchantmentEmptyEpilepsyFire() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        if (arrow.getOwner() == null) {
            return;
        }

        if (evt.getRayTraceResult().getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        if (!(((net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult()).getEntity() instanceof LivingEntity)) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        LivingEntity attacker = (LivingEntity) arrow.getOwner();
        LivingEntity victim = (LivingEntity) ((net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult()).getEntity();

        Enchantment emptyEpilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class);
        if (emptyEpilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(emptyEpilepsyFire, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        // 对攻击者造成癫火伤害（创造模式玩家免疫）
        if (!(attacker instanceof Player) || !((Player) attacker).isCreative()) {
            attacker.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    CarianStylePotion.EPILEPSY_FIRE_BURNING.get(),
                    BURN_DURATION,
                    0
            ));

            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > DAMAGE_TICKS || !attacker.isAlive()) {
                        this.cancel();
                        return;
                    }

                    float damage = attacker.getMaxHealth() * 0.15f / 60;
                    if (attacker.getHealth() - damage * 2 > 0) {
                        EntityLivingUtil.damageHealthDirectly(attacker, damage);
                    } else {
                        EntityLivingUtil.kill(attacker, NewDamageSource.epilepsyFire(attacker.level()));
                        this.cancel();
                    }
                }
            }.start();
        }

        // 对受击者造成癫火伤害
        victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                CarianStylePotion.EPILEPSY_FIRE_BURNING.get(),
                BURN_DURATION,
                0
        ));

        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > DAMAGE_TICKS || !victim.isAlive()) {
                    this.cancel();
                    return;
                }

                float damage = attacker.getMaxHealth() * 0.15f * effectiveLevel / 60;
                if (victim.getHealth() - damage * 2 > 0) {
                    EntityLivingUtil.damageHealthDirectly(victim, damage);
                } else {
                    EntityLivingUtil.kill(victim, NewDamageSource.epilepsyFire(victim.level()));
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}