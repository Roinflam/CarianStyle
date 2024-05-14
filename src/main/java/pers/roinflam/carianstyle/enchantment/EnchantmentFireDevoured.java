package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentFireDevoured extends RaryBase {

    public EnchantmentFireDevoured(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "fire_devoured");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.FIRE_DEVOURED;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getImmediateSource() instanceof EntityLivingBase) {
                DamageSource damageSource = evt.getSource();
                EntityLivingBase hurter = evt.getEntityLiving();
                @Nullable EntityLivingBase attacker = (EntityLivingBase) damageSource.getImmediateSource();
                if (!attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), attacker.getHeldItem(attacker.getActiveHand()));
                    if (ConfigLoader.levelLimit) {
                        bonusLevel = Math.min(bonusLevel, 10);
                    }
                    if (bonusLevel > 0) {
                        if (EntityUtil.getFire(attacker) > 0) {
                            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                    EntityLivingBase.class,
                                    hurter,
                                    bonusLevel,
                                    entityLivingBase -> !entityLivingBase.equals(hurter) || entityLivingBase.equals(attacker)
                            );
                            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                                entityLivingBase.attackEntityFrom(DamageSource.IN_FIRE, evt.getAmount() * bonusLevel * 0.15f);
                                if (EntityUtil.getFire(entityLivingBase) < 200) {
                                    entityLivingBase.setFire(10);
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
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

}
