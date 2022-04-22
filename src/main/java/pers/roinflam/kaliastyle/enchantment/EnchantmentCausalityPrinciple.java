package pers.roinflam.kaliastyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;
import pers.roinflam.kaliastyle.utils.util.EntityUtil;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends Enchantment {
    private static final HashMap<UUID, Integer> CAUSALITY_PRINCIPLE = new HashMap<>();

    public EnchantmentCausalityPrinciple(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "causality_principle");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                CAUSALITY_PRINCIPLE.clear();
            }

        }.start();
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.CAUSALITY_PRINCIPLE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (itemStack != null) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (bonusLevel > 0) {
                        if (CAUSALITY_PRINCIPLE.getOrDefault(hurter.getUniqueID(), 0) >= 5) {
                            CAUSALITY_PRINCIPLE.remove(hurter.getUniqueID());
                            List<Entity> entities = EntityUtil.getNearbyEntities(
                                    hurter,
                                    bonusLevel * 2,
                                    bonusLevel * 2,
                                    entity -> entity instanceof EntityLivingBase && !entity.equals(hurter)
                            );
                            for (Entity entity : entities) {
                                EntityLivingBase entityLivingBase = (EntityLivingBase) entity;
                                entityLivingBase.attackEntityFrom(DamageSource.causeMobDamage(hurter), (float) (evt.getAmount() * bonusLevel * 0.5));
                            }
                        } else {
                            CAUSALITY_PRINCIPLE.put(hurter.getUniqueID(), CAUSALITY_PRINCIPLE.getOrDefault(hurter.getUniqueID(), 0) + 1);
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
        return 10 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

}
