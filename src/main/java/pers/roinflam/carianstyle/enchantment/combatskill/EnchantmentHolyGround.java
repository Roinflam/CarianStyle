package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.SoundEvents;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemShield;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.RaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends RaryBase {

    public EnchantmentHolyGround(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "holy_ground");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.HOLY_GROUND;
    }

    @SubscribeEvent
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(EntityLivingBase.class, hurter, 16, entityLivingBase -> entityLivingBase.getClass() == (hurter.getClass()));
            for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                if (entityLivingBase.isHandActive()) {
                    @Nonnull ItemStack itemStack = entityLivingBase.getHeldItem(entityLivingBase.getActiveHand());
                    if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                        int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);

                if (bonusLevel > 0) {
                            evt.setAmount(evt.getAmount() - evt.getAmount() * bonusLevel * 0.05f);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        if (evt.getEntity().ticksExisted % 60 == 0) {
            EntityLivingBase entityLiving = evt.getEntityLiving();
            if (entityLiving.isHandActive()) {
                @Nonnull ItemStack itemStack = entityLiving.getHeldItem(entityLiving.getActiveHand());
                if (!itemStack.isEmpty() && itemStack.getItem() instanceof ItemShield) {
                    int bonusLevel = EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                    
                if (bonusLevel > 0) {
                        @Nonnull List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                                EntityLivingBase.class,
                                entityLiving,
                                16,
                                entityLivingBase -> entityLivingBase.getClass() == (entityLiving.getClass())
                        );
                        for (@Nonnull EntityLivingBase entityLivingBase : entities) {
                            boolean used = false;
                            if (entityLivingBase.getHealth() < entityLivingBase.getMaxHealth()) {
                                entityLivingBase.heal(entityLivingBase.getMaxHealth() * bonusLevel * 0.015f);
                                used = true;
                            }
                            float maxAbsorption = entityLivingBase.getMaxHealth() / 3 * bonusLevel;
                            if (entityLivingBase.getAbsorptionAmount() < maxAbsorption) {
                                entityLivingBase.setAbsorptionAmount(Math.min(entityLivingBase.getAbsorptionAmount() + entityLivingBase.getMaxHealth() * bonusLevel * 0.03f, maxAbsorption));
                                used = true;
                            }
                            if (used) {
                                entityLivingBase.playSound(SoundEvents.ENTITY_PLAYER_LEVELUP, 1, 3);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return !CarianStyleEnchantments.COMBAT_SKILL.contains(ench);
    }

}
