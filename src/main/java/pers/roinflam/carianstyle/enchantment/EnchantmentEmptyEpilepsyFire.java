package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 空癫火附魔
 *
 * 弓箭附魔,双刃剑效果
 * 箭矢命中敌人时：
 * - 对敌人造成持续3秒的癫火伤害(攻击者最大生命值 × 15% × 等级)
 * - 对自己造成持续3秒的癫火伤害(自己最大生命值 × 15%)
 * - 创造模式玩家免疫自损
 */
@AutoRegisterEnchantment(
        id = "empty_epilepsy_fire",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentEmptyEpilepsyFire extends EnchantmentBase {

    private static final int BURN_DURATION = 65;  // 3秒 + 5 tick
    private static final int DAMAGE_TICKS = 60;   // 持续伤害60 tick (3秒)

    public EnchantmentEmptyEpilepsyFire() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中实体时对双方施加癫火伤害
     * 注意：ProjectileImpactEvent基类无模板方法,保留静态监听器
     */
    @SubscribeEvent
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有射击者且击中实体
        if (evt.getArrow().shootingEntity == null) {
            return;
        }
        if (!(evt.getRayTraceResult().entityHit instanceof EntityLivingBase)) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;
        EntityLivingBase victim = (EntityLivingBase) evt.getRayTraceResult().entityHit;
        Enchantment emptyEpilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEmptyEpilepsyFire.class);

        if (emptyEpilepsyFire == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                emptyEpilepsyFire,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        final int effectiveLevel = level;

        // 对攻击者造成癫火伤害(创造模式玩家免疫)
        if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
            attacker.addPotionEffect(new PotionEffect(
                    CarianStylePotion.EPILEPSY_FIRE_BURNING,
                    BURN_DURATION,
                    0
            ));

            new SynchronizationTask(5, 1) {
                private int tick = 0;

                @Override
                public void run() {
                    if (++tick > DAMAGE_TICKS || !attacker.isEntityAlive()) {
                        this.cancel();
                        return;
                    }

                    // 每tick造成 最大生命值×15%/60 的伤害
                    float damage = attacker.getMaxHealth() * 0.15f / 60;
                    if (attacker.getHealth() - damage * 2 > 0) {
                        attacker.setHealth(attacker.getHealth() - damage);
                    } else {
                        EntityLivingUtil.kill(attacker, NewDamageSource.EPILEPSY_FIRE);
                        this.cancel();
                    }
                }
            }.start();
        }

        // 对受击者造成癫火伤害
        victim.addPotionEffect(new PotionEffect(
                CarianStylePotion.EPILEPSY_FIRE_BURNING,
                BURN_DURATION,
                0
        ));

        new SynchronizationTask(5, 1) {
            private int tick = 0;

            @Override
            public void run() {
                if (++tick > DAMAGE_TICKS || !victim.isEntityAlive()) {
                    this.cancel();
                    return;
                }

                // 每tick造成 攻击者最大生命值×15%×等级/60 的伤害
                float damage = attacker.getMaxHealth() * 0.15f * effectiveLevel / 60;
                if (victim.getHealth() - damage * 2 > 0) {
                    victim.setHealth(victim.getHealth() - damage);
                } else {
                    EntityLivingUtil.kill(victim, NewDamageSource.EPILEPSY_FIRE);
                    this.cancel();
                }
            }
        }.start();
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}