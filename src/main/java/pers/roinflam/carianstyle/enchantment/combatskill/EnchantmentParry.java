package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentParry extends Enchantment {
    private static final HashMap<UUID, Integer> PARRY = new HashMap<>();
    private static final Set<UUID> COOLDING = new HashSet<>();

    public EnchantmentParry(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "parry");
        CarianStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.PARRY;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt_Shield(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getAmount() <= 0) {
                if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    if (hurter.isHandActive()) {
                        ItemStack itemStack = hurter.getHeldItem(hurter.getActiveHand());
                        if (itemStack != null && itemStack.getItem() instanceof ItemShield) {
                            int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            if (bonusLevel > 0) {
                                if (!COOLDING.contains(hurter.getUniqueID()) && !PARRY.containsKey(hurter.getUniqueID())) {
                                    PARRY.put(hurter.getUniqueID(), bonusLevel);
                                    new SynchronizationTask(10) {

                                        @Override
                                        public void run() {
                                            PARRY.remove(hurter.getUniqueID());
                                        }

                                    }.start();
                                }
                            }
                        }
                    }
                }

            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt_Attack(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (PARRY.containsKey(attacker.getUniqueID())) {
                    evt.setAmount((float) (evt.getAmount() + evt.getAmount() * PARRY.get(attacker.getUniqueID()) * 0.25));
                    PARRY.remove(attacker.getUniqueID());
                    COOLDING.add(attacker.getUniqueID());
                    new SynchronizationTask(40) {

                        @Override
                        public void run() {
                            COOLDING.remove(attacker.getUniqueID());
                        }

                    }.start();
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
        return 5;
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 10 + (enchantmentLevel - 1) * 10;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
