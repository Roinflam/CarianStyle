package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber
public class EnchantmentAncestralSpirits extends VeryRaryBase {

    public EnchantmentAncestralSpirits(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "ancestral_spirits");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.ANCESTRAL_SPIRITS;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                int bonusLevel = 0;
                for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                    if (!itemStack.isEmpty()) {
                        bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    }
                }
                
                if (bonusLevel > 0) {
                    if (evt.getSource().isMagicDamage()) {
                        evt.setAmount(evt.getAmount() * 0.5f);
                    }
                    if (hurter.isEntityAlive()) {
                        float heal = (hurter.getMaxHealth() - hurter.getHealth()) * 0.05f / 20;
                        new SynchronizationTask(10, 10) {
                            private int tick = 0;

                            @Override
                            public void run() {
                                tick += 10;
                                if (tick > 200 || !hurter.isEntityAlive()) {
                                    this.cancel();
                                    return;
                                }
                                hurter.heal(heal);
                            }

                        }.start();
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (CarianStyleEnchantments.RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.RECOLLECT.contains(ench);
    }
}
