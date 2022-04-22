package pers.roinflam.kaliastyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends Enchantment {
    private static final Set<UUID> GOLDEN_LAW = new HashSet<>();

    public EnchantmentGoldenLaw(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "golden_law");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.GOLDEN_LAW;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                    if (attacker.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.15));
                        }
                    }
                }
                if (!evt.getSource().canHarmInCreative()) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    if (hurter.getHeldItemMainhand() != null) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                        if (bonusLevel > 0) {
                            evt.setAmount((float) (evt.getAmount() - evt.getAmount() * 0.15));
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (hurter.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        if (evt.getAmount() <= hurter.getHealth() * 0.15) {
                            evt.setCanceled(true);
                        } else if (!GOLDEN_LAW.contains(hurter.getUniqueID())) {
                            evt.setCanceled(true);
                            GOLDEN_LAW.add(hurter.getUniqueID());
                            new SynchronizationTask(200) {

                                @Override
                                public void run() {
                                    GOLDEN_LAW.remove(hurter.getUniqueID());
                                }

                            }.start();
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
        return KaliaStyleEnchantments.RECOLLECT_ENCHANTABILITY;
    }

    @Override
    public int getMaxEnchantability(int enchantmentLevel) {
        return getMinEnchantability(enchantmentLevel) * 2;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !KaliaStyleEnchantments.RECOLLECT.contains(ench);
    }
}
