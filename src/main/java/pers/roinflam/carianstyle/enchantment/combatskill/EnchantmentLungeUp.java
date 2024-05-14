package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static net.minecraft.init.MobEffects.SLOWNESS;

@Mod.EventBusSubscriber
public class EnchantmentLungeUp extends RaryBase {

    public EnchantmentLungeUp(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "lunge_up");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.LUNGE_UP;
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (attacker.isSprinting()) {
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));

                if (bonusLevel > 0) {
                            attacker.setSprinting(false);
                            attacker.addPotionEffect(new PotionEffect(SLOWNESS, 10, 6));
                            new SynchronizationTask(5) {

                                @Override
                                public void run() {
                                    if (attacker.isEntityAlive()) {
                                        @Nonnull LivingKnockBackEvent livingKnockBackEvent = ForgeHooks.onLivingKnockBack(hurter, attacker, bonusLevel * 0.3f, 0, 0);
                                        if (!livingKnockBackEvent.isCanceled()) {
                                            hurter.motionY = livingKnockBackEvent.getStrength();
                                        }
                                    }
                                }

                            }.start();
                            evt.setAmount(evt.getAmount() + evt.getAmount() * bonusLevel * 0.15f);
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((15 + (enchantmentLevel - 1) * 5) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
