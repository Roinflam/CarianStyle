package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber
public class EnchantmentConcealingVeil extends VeryRaryBase {
    private static final List<UUID> BATTLE = new ArrayList<>();

    public EnchantmentConcealingVeil(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "concealing_veil");

        new SynchronizationTask(6000, 6000) {

            @Override
            public void run() {
                BATTLE.clear();
            }

        }.start();
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CONCEALING_VEIL;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getEntityLiving() instanceof EntityPlayer) {
                EntityPlayer hurter = (EntityPlayer) evt.getEntityLiving();
                BATTLE.add(hurter.getUniqueID());
                new SynchronizationTask(60) {

                    @Override
                    public void run() {
                        BATTLE.remove(hurter.getUniqueID());
                    }

                }.start();
            }
            if (evt.getSource().getImmediateSource() instanceof EntityPlayer) {
                EntityPlayer attacker = (EntityPlayer) evt.getSource().getImmediateSource();
                BATTLE.add(attacker.getUniqueID());
                new SynchronizationTask(60) {

                    @Override
                    public void run() {
                        BATTLE.remove(attacker.getUniqueID());
                    }

                }.start();
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                EntityPlayer entityPlayer = evt.player;
                if (entityPlayer.isSneaking() && !BATTLE.contains(entityPlayer.getUniqueID())) {
                    int bonusLevel = 0;
                    for (ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (bonusLevel > 0) {
                        entityPlayer.addPotionEffect(new PotionEffect(CarianStylePotion.STEALTH, 2, 0));
                    }
                }
            }
        }
    }


    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30;
    }


    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }

}
