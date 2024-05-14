package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentFreezingEarthquake extends RaryBase {

    public EnchantmentFreezingEarthquake(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "freezing_earthquake");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.FREEZING_EARTHQUAKE;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
                if (evt.getAmount() >= evt.getEntityLiving().getMaxHealth() * 0.25) {
                    EntityLivingBase hurter = evt.getEntityLiving();
                    int bonusLevel = 0;
                    for (@Nonnull ItemStack itemStack : hurter.getArmorInventoryList()) {
                        if (!itemStack.isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                        }
                    }
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                hurter,
                                3 + (bonusLevel - 1) * 2,
                                entityLivingBase -> entityLivingBase.onGround && !entityLivingBase.equals(hurter)
                        );
                        for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                            if (entityLivingBase.onGround) {
                                @Nonnull LivingKnockBackEvent livingKnockBackEvent = ForgeHooks.onLivingKnockBack(entityLivingBase, hurter, bonusLevel * 0.35f, 0, 0);
                                if (!livingKnockBackEvent.isCanceled()) {
                                    entityLivingBase.motionY = livingKnockBackEvent.getStrength();
                                    entityLivingBase.addPotionEffect(new PotionEffect(CarianStylePotion.FROSTBITE, bonusLevel * 5 * 20, bonusLevel - 1));
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
        return (int) ((15 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

}
