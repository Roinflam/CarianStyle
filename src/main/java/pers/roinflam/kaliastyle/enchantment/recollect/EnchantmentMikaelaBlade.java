package pers.roinflam.kaliastyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.kaliastyle.init.KaliaStyleEnchantments;
import pers.roinflam.kaliastyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.kaliastyle.utils.util.EnchantmentUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentMikaelaBlade extends Enchantment {
    private static final HashMap<UUID, Integer> COMMB = new HashMap<>();

    public EnchantmentMikaelaBlade(Rarity rarityIn, EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(rarityIn, typeIn, slots);
        EnchantmentUtil.registerEnchantment(this, "mikaela_blade");
        KaliaStyleEnchantments.ENCHANTMENTS.add(this);

        new SynchronizationTask(40, 40) {

            @Override
            public void run() {
                for (UUID uuid : new ArrayList<>(COMMB.keySet())) {
                    if (COMMB.get(uuid) > 1) {
                        COMMB.put(uuid, COMMB.get(uuid) - 1);
                    } else {
                        COMMB.remove(uuid);
                    }
                }
            }

        }.start();
    }

    public static Enchantment getEnchantment() {
        return KaliaStyleEnchantments.MIKAELA_BLADE;
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.getHeldItemMainhand() != null) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItemMainhand());
                    if (bonusLevel > 0) {
                        evt.setAmount((float) (evt.getAmount() * 0.4 + evt.getAmount() * COMMB.getOrDefault(attacker.getUniqueID(), 0) * 0.2));
                        COMMB.put(attacker.getUniqueID(), COMMB.getOrDefault(attacker.getUniqueID(), 0) + 1);
                    }
                }
            }
            EntityLivingBase hurter = evt.getEntityLiving();
            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * COMMB.getOrDefault(hurter.getUniqueID(), 0) * 0.1));
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
