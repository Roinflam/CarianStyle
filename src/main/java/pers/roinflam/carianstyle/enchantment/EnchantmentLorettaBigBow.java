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
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

@Mod.EventBusSubscriber
public class EnchantmentLorettaBigBow extends VeryRaryBase {

    public EnchantmentLorettaBigBow(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "loretta_big_bow");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LORETTA_BIG_BOW;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                if (bonusLevel > 0) {
                    entityArrow.setDamage(entityArrow.getDamage() + entityArrow.getDamage() * 0.5);
                    Explosion explosion = attacker.world.createExplosion(attacker, entityArrow.posX, entityArrow.posY, entityArrow.posZ, (float) EntityUtil.getFire(entityArrow) > 0 ? 3 : 2, false);
                }
            }
        }

    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25;
    }

}
