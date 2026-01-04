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
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 灾厄附魔（诅咒）
 *
 * 受击伤害×2
 * 每tick 2%概率吸引32格内怪物
 */
@AutoRegisterEnchantment(
        id = "calamity",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        isCurse = true
)
@Mod.EventBusSubscriber
public class EnchantmentCalamity extends EnchantmentBase {

    public EnchantmentCalamity() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{EntityEquipmentSlot.CHEST});
    }

    private static int getArmorLevel(EntityLivingBase entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(calamity, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    private static int getTotalLevel(EntityLivingBase entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;

        if (!entity.getHeldItem(entity.getActiveHand()).isEmpty()) {
            totalLevel += EnchantmentHelper.getEnchantmentLevel(
                    calamity,
                    entity.getHeldItem(entity.getActiveHand()));
        }

        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(calamity, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@Nonnull LivingHurtEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();

        int totalLevel = getArmorLevel(victim);
        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * 2);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(@Nonnull TickEvent.PlayerTickEvent evt) {
        if (evt.player.world.isRemote) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (!RandomUtil.percentageChance(2)) {
            return;
        }

        EntityPlayer player = evt.player;
        if (!player.isEntityAlive()) {
            return;
        }

        int totalLevel = getTotalLevel(player);
        if (totalLevel <= 0) {
            return;
        }

        List<EntityMob> nearbyMobs = EntityUtil.getNearbyEntities(
                EntityMob.class,
                player,
                32
        );

        for (EntityMob mob : nearbyMobs) {
            EntityLivingBase currentTarget = mob.getAttackTarget();

            if (currentTarget == null || !currentTarget.isEntityAlive()) {
                if (RandomUtil.percentageChance(25)) {
                    mob.setAttackTarget(player);
                }
            } else if (!currentTarget.equals(player)) {
                if (RandomUtil.percentageChance(50)) {
                    mob.setAttackTarget(player);
                }
            }
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }
}