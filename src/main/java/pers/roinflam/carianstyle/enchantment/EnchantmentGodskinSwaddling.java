package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGodskinSwaddling extends RaryBase {
    private static final HashMap<UUID, Integer> ATTACK = new HashMap<>();

    public EnchantmentGodskinSwaddling(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "godskin_swaddling");

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                ATTACK.clear();
            }

        }.start();
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.GODSKIN_SWADDLING;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        if (ATTACK.getOrDefault(attacker.getUniqueID(), 0) == 3) {
                            ATTACK.remove(attacker.getUniqueID());

                            attacker.heal((float) (attacker.getMaxHealth() * 0.03 + attacker.getMaxHealth() * (bonusLevel - 1) * 0.01));
                        } else {
                            ATTACK.put(attacker.getUniqueID(), ATTACK.getOrDefault(attacker.getUniqueID(), 0) + 1);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 20 + (enchantmentLevel - 1) * 10;
    }

}
