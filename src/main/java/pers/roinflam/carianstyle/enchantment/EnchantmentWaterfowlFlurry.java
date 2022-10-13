package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

@Mod.EventBusSubscriber
public class EnchantmentWaterfowlFlurry extends RaryBase {

    public EnchantmentWaterfowlFlurry(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "waterfowl_flurry");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.WATERFOWL_FLURRY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        DamageSource damageSource = evt.getSource();
                        if (!damageSource.damageType.equals("waterfowlDance") && !damageSource.damageType.equals("noDeathBlade")) {
                            float damage = (evt.getAmount() / (bonusLevel + 1)) * (1 - bonusLevel * 0.3f);
                            evt.setAmount(damage);

                            damageSource.damageType = "waterfowlDance";

                            new SynchronizationTask(1, 2) {
                                private int time = 0;

                                @Override
                                public void run() {
                                    if (++time > bonusLevel || !hurter.isEntityAlive()) {
                                        this.cancel();
                                        return;
                                    }
                                    hurter.hurtResistantTime = hurter.maxHurtResistantTime / 2;
                                    hurter.attackEntityFrom(damageSource, damage);
                                }

                            }.start();
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 30 + (enchantmentLevel - 1) * 30;
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}
