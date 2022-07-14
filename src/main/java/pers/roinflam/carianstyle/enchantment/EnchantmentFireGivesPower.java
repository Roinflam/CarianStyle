package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.UncommonBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EnchantmentUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

@Mod.EventBusSubscriber
public class EnchantmentFireGivesPower extends UncommonBase {

    public EnchantmentFireGivesPower(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "fire_gives_power");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.FIRE_GIVES_POWER;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_Attack(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (bonusLevel > 0) {
                        if (EntityUtil.getFire(attacker) > 0) {
                            if (EntityUtil.getFire(attacker) < 200) {
                                attacker.setFire(10);
                            }
                            evt.setAmount((float) (evt.getAmount() + evt.getAmount() * 0.075 * bonusLevel));
                        } else {
                            if (!(attacker instanceof EntityPlayer) || !((EntityPlayer) attacker).isCreative()) {
                                attacker.setFire(10);
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage_Hurt(LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            if (EntityUtil.getFire(hurter) > 0) {
                if (!evt.getSource().canHarmInCreative() && !evt.getSource().isMagicDamage()) {
                    if (!hurter.getHeldItem(hurter.getActiveHand()).isEmpty()) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), hurter.getHeldItem(hurter.getActiveHand()));
                        if (bonusLevel > 0) {
                            evt.setAmount((float) (evt.getAmount() - evt.getAmount() * 0.0375 * bonusLevel));
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return 5 + (enchantmentLevel - 1) * 10;
    }

}
