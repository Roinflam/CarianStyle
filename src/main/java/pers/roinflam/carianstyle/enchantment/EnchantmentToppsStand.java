package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentToppsStand extends Enchantment {

    public EnchantmentToppsStand(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "topps_stand");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.TOPPS_STAND;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().isMagicDamage()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                List<Entity> entities = EntityUtil.getNearbyEntities(
                        hurter,
                        6,
                        6,
                        entity -> entity instanceof EntityLivingBase
                );
                if(evt.getSource().getTrueSource() instanceof EntityLivingBase){
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    entities.addAll(EntityUtil.getNearbyEntities(
                            attacker,
                            6,
                            6,
                            entity -> entity instanceof EntityLivingBase
                    ));
                }
                for (Entity entity : entities) {
                    EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                    for (ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                        if (itemStack != null) {
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
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 5 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }

}
