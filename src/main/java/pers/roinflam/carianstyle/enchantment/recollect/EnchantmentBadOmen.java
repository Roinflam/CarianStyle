package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber
public class EnchantmentBadOmen extends VeryRaryBase {

    public EnchantmentBadOmen(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "bad_omen");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.BAD_OMEN;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (!evt.getSource().canHarmInCreative()) {
                EntityLivingBase hurter = evt.getEntityLiving();
                if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                    @Nullable EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                    if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            if (hurter.isPotionActive(CarianStylePotion.BAD_OMEN)) {
                                float damage = evt.getAmount();
                                if (damage * 0.5 >= hurter.getHealth()) {
                                    EntityLivingUtil.kill(hurter, evt.getSource().setDamageAllowedInCreativeMode());
                                } else {
                                    hurter.setHealth(hurter.getHealth() - damage * 0.5f);
                                    evt.setAmount(damage * 0.5f);
                                }
                            }
                            hurter.addPotionEffect(new PotionEffect(CarianStylePotion.BAD_OMEN, 200, 0));
                        }
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
