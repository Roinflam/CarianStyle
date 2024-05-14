package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.world.Explosion;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentLorettaTrick extends VeryRaryBase {

    public EnchantmentLorettaTrick(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "loretta_trick");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LORETTA_TRICK;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                if (ConfigLoader.levelLimit) {
                    bonusLevel = Math.min(bonusLevel, 10);
                }
                if (bonusLevel > 0) {
                    entityArrow.setDamage(entityArrow.getDamage() - entityArrow.getDamage() * 0.25);
                    new SynchronizationTask(1, 5) {
                        private int time = 0;

                        @Override
                        public void run() {
                            if (++time > 4) {
                                this.cancel();
                                return;
                            }
                            @Nonnull Explosion explosion = attacker.world.createExplosion(attacker, entityArrow.posX - 2.5 + RandomUtil.getInt(0, 5), entityArrow.posY, entityArrow.posZ - 2.5 + RandomUtil.getInt(0, 5), EntityUtil.getFire(entityArrow) > 0 ? 4 : 3, false);
                        }

                    }.start();
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.LORETTA_BIG_BOW);
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
