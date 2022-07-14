package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentCorruptedWingSword extends UncommonBase {
    private static final HashMap<UUID, Integer> COMMB = new HashMap<>();

    public EnchantmentCorruptedWingSword(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "corrupted_wing_sword");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CORRUPTED_WING_SWORD;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        if (COMMB.getOrDefault(attacker.getUniqueID(), 0) < 20) {
                            COMMB.put(attacker.getUniqueID(), COMMB.getOrDefault(attacker.getUniqueID(), 0) + 1);
                            new SynchronizationTask(300) {

                                @Override
                                public void run() {
                                    if (COMMB.get(attacker.getUniqueID()) > 1) {
                                        COMMB.put(attacker.getUniqueID(), COMMB.get(attacker.getUniqueID()) - 1);
                                    } else {
                                        COMMB.remove(attacker.getUniqueID());
                                    }
                                }

                            }.start();
                            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * (COMMB.get(attacker.getUniqueID()) / 4) * 0.03 * bonusLevel));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 15 + (enchantmentLevel - 1) * 5;
    }

}
