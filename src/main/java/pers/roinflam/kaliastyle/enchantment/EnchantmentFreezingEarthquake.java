package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.init.KaliaStylePotion;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;
import pers.roinflam.kaliastyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends Enchantment {

    public EnchantmentFreezingEarthquake(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "freezing_earthquake");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.FREEZING_EARTHQUAKE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                if (evt.getAmount() >= evt.getEntityLiving().getMaxHealth() * 0.25) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (itemStack != null) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (bonusLevel > 0) {
                        List<Entity> entities = EntityUtil.getNearbyEntities(
                                hurter,
                                3 + (bonusLevel - 1) * 2,
                                3 + (bonusLevel - 1) * 2,
                                entity -> entity.onGround && entity instanceof EntityLivingBase && !entity.equals(hurter)
                        );
                        for (Entity entity : entities) {
                            EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                            if (entityLivingBase.onGround) {
                                LivingKnockBackEvent livingKnockBackEvent = ForgeHooks.onLivingKnockBack(entityLivingBase, hurter, bonusLevel * 0.35f, 0, 0);
                                if (!livingKnockBackEvent.isCanceled()) {
                                    entityLivingBase.motionY = livingKnockBackEvent.getStrength();
                                    entityLivingBase.addPotionEffect(new PotionEffect(KaliaStylePotion.FROSTBITE, bonusLevel * 5 * 20, bonusLevel - 1));
                                }
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
        return 3;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 15 + (enchantmentLevel - 1) * 15;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

}
