package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentToppsStand extends VeryRaryBase {

    public EnchantmentToppsStand(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "topps_stand");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.TOPPS_STAND;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().isMagicDamage()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                        EntityLivingBase.class,
                        hurter,
                        6
                );
                if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    entities.addAll(EntityUtil.getNearbyEntities(
                            EntityLivingBase.class,
                            attacker,
                            6
                    ));
                }
                for (Entity entity : entities) {
                    EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                    for (ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            if (EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack) > 0) {
                                evt.setCanceled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 5 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

}
