package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

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

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CAUSALITY_PRINCIPLE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (bonusLevel > 0) {
                        if (CAUSALITY_PRINCIPLE.getOrDefault(hurter.getUniqueID(), 0) >= 5) {
                            CAUSALITY_PRINCIPLE.remove(hurter.getUniqueID());
                            List<EntityLivingBase> entities = hurter.world.getEntitiesWithinAABB(
                                    EntityLivingBase.class,
                                    new AxisAlignedBB(hurter.getPosition()).expand(bonusLevel * 3, bonusLevel * 3, bonusLevel * 3),
                                    entityLivingBase -> !entityLivingBase.equals(hurter)
                            );
                            for (EntityLivingBase entityLivingBase : entities) {
                                entityLivingBase.attackEntityFrom(DamageSource.causeMobDamage(hurter), (float) (evt.getAmount() * bonusLevel * 0.75));
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
        return 10 + (enchantmentLevel - 1) * 10;
    }

}
