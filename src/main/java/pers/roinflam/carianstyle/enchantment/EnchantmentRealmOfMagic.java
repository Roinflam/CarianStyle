package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentRealmOfMagic extends VeryRaryBase {

    public EnchantmentRealmOfMagic(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "realm_of_magic");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.REALM_OF_MAGIC;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                if (evt.getSource().isMagicDamage()) {
                    EntityLivingBase attacker = (EntityLivingBase) evt.getSource().getTrueSource();
                    List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                            EntityLivingBase.class,
                            attacker,
                            6,
                            entityLivingBase -> entityLivingBase.getClass() == (attacker.getClass())
                    );
                    for (EntityLivingBase entityLivingBase : entities) {
                        for (ItemStack itemStack : entityLivingBase.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                if (EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack) > 0) {
                                    evt.setAmount(evt.getAmount() + evt.getAmount() * 0.5f);
                                    break;
                                }
                            }
                        }
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
    public boolean isTreasureEnchantment() {
        return true;
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(CarianStyleEnchantments.TOPPS_STAND);
    }
}
