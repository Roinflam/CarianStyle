package pers.roinflam.kaliastyle.enchantment;

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
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;
import pers.roinflam.kaliastyle.utils.util.EntityUtil;

@Mod.EventBusSubscriber
public class EnchantmentLorettaBigBow extends Enchantment {

    public EnchantmentLorettaBigBow(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "loretta_big_bow");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.LORETTA_BIG_BOW;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                if (bonusLevel > 0) {
                    entityArrow.setDamage(entityArrow.getDamage() + entityArrow.getDamage() * 0.5);
                    System.out.println(EntityUtil.getFire(entityArrow));
                    Explosion explosion = attacker.world.createExplosion(attacker, entityArrow.posX, entityArrow.posY, entityArrow.posZ, (float) EntityUtil.getFire(entityArrow) > 0 ? 3 : 2, false);
                }
            }
        }

    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

}