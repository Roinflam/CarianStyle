package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentDragonBreathCorruption extends Enchantment {

    public EnchantmentDragonBreathCorruption(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "dragon_breath_corruption");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.DRAGON_BREATH_CORRUPTION;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(ProjectileImpactEvent.Arrow evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getArrow().shootingEntity != null && evt.getRayTraceResult().entityHit == null) {
                EntityArrow entityArrow = evt.getArrow();
                EntityLivingBase attacker = (EntityLivingBase) evt.getArrow().shootingEntity;
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                entityArrow,
                                bonusLevel * 2,
                                bonusLevel * 2,
                                entity -> entity instanceof EntityLivingBase
                        );
                        for (Entity entity : entities) {
                            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                            entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.SCARLET_ROT, bonusLevel * 5 * 20, bonusLevel - 1));
                        }
                    }
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
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 25 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

}
