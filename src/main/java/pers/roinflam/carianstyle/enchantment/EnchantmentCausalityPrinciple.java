package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentCausalityPrinciple extends RaryBase {
    private static final HashMap<UUID, Integer> CAUSALITY_PRINCIPLE = new HashMap<>();

    public EnchantmentCausalityPrinciple(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "causality_principle");

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                CAUSALITY_PRINCIPLE.clear();
            }

        }.start();
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CAUSALITY_PRINCIPLE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (CAUSALITY_PRINCIPLE.getOrDefault(hurter.getUniqueID(), 0) >= 5) {
                            CAUSALITY_PRINCIPLE.remove(hurter.getUniqueID());
                            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                    EntityLivingBase.class,
                                    hurter,
                                    bonusLevel * 3,
                                    entityLivingBase -> !entityLivingBase.equals(hurter)
                            );
                            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                entityLivingBase.attackEntityFrom(DamageSource.causeMobDamage(hurter), evt.getAmount() * bonusLevel * 0.75f);
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
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

}
