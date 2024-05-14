package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentCalamity extends VeryRaryBase {

    public EnchantmentCalamity(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "calamity");
    }

    @Nonnull
    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CALAMITY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
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
                evt.setAmount(evt.getAmount() * 2);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.START)) {
                if (RandomUtil.percentageChance(2)) {
                    @Nonnull EntityPlayer entityPlayer = evt.player;
                    if (entityPlayer.isEntityAlive()) {
                        int bonusLevel = 0;
                        if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItem(entityPlayer.getActiveHand()));
                        }
                        for (@Nonnull ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (ConfigLoader.levelLimit) {
                            bonusLevel = Math.min(bonusLevel, 10);
                        }
                        if (bonusLevel > 0) {
                            @Nonnull List<EntityMob> entities = EntityUtil.getNearbyEntities(
                                    EntityMob.class,
                                    entityPlayer,
                                    32
                            );
                            for (@Nonnull EntityMob entityMob : entities) {
                                @Nullable EntityLivingBase attackTarget = entityMob.getAttackTarget();
                                if (attackTarget == null || !entityMob.getAttackTarget().isEntityAlive()) {
                                    if (RandomUtil.percentageChance(25)) {
                                        entityMob.setAttackTarget(entityPlayer);
                                    }
                                } else {
                                    if (!attackTarget.equals(entityPlayer)) {
                                        if (RandomUtil.percentageChance(50)) {
                                            entityMob.setAttackTarget(entityPlayer);
                                        }
                                    }
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
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean isCurse() {
        return true;
    }
}
