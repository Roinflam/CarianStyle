package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import pers.roinflam.carianstyle.base.enchantment.rarity.VeryRaryBase;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.List;

@Mod.EventBusSubscriber
public class EnchantmentCalamity extends VeryRaryBase {

    public EnchantmentCalamity(EnumEnchantmentType typeIn, EntityEquipmentSlot[] slots) {
        super(typeIn, slots, "calamity");
    }

    public static Enchantment getEnchantment() {
        return CarianStyleEnchantments.CALAMITY;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent evt) {
        if (!evt.getEntity().world.isRemote) {
            EntityLivingBase hurter = evt.getEntityLiving();
            int bonusLevel = 0;
            for (ItemStack itemStack : hurter.getArmorInventoryList()) {
                if (!itemStack.isEmpty()) {
                    bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                }
            }
            if (bonusLevel > 0) {
                evt.setAmount(evt.getAmount() * 2);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent evt) {
        if (!evt.player.world.isRemote) {
            if (evt.phase.equals(TickEvent.Phase.END)) {
                if (RandomUtil.percentageChance(2)) {
                    EntityPlayer entityPlayer = evt.player;
                    if (entityPlayer.isEntityAlive()) {
                        int bonusLevel = 0;
                        if (!entityPlayer.getHeldItem(entityPlayer.getActiveHand()).isEmpty()) {
                            bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), entityPlayer.getHeldItem(entityPlayer.getActiveHand()));
                        }
                        for (ItemStack itemStack : entityPlayer.getArmorInventoryList()) {
                            if (!itemStack.isEmpty()) {
                                bonusLevel += EnchantmentHelper.getEnchantmentLevel(getEnchantment(), itemStack);
                            }
                        }
                        if (bonusLevel > 0) {
                            List<EntityMob> entities = entityPlayer.world.getEntitiesWithinAABB(
                                    EntityMob.class,
                                    new AxisAlignedBB(entityPlayer.getPosition()).expand(32, 32, 32)
                            );
                            for (EntityMob entityMob : entities) {
                                EntityLivingBase attackTarget = entityMob.getAttackTarget();
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
        return 35;
    }

    @Override
    public boolean isCurse() {
        return true;
    }
}
