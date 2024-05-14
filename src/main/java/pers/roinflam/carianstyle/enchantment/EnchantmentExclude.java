package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentExclude extends RaryBase {

    public EnchantmentExclude(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "exclude");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.EXCLUDE;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            if (evt.getSource().getTrueSource() instanceof EntityLivingBase) {
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
                            (int) (5 + (bonusLevel - 1) * 0.75),
                            entityLivingBase -> !entityLivingBase.equals(hurter)
                    );
                    for (@Nonnull Entity entity : entities) {
                        EntityLivingBase entityLivingBase = (EntityLivingBase) entity;

                        double x = hurter.posX - entityLivingBase.posX;
                        double z = hurter.posZ - entityLivingBase.posZ;
                        entityLivingBase.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) entityLivingBase.rotationYaw);
                        entityLivingBase.knockBack(entity, 0.5f, x, z);
                    }
                }
            }
        }

    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

}
