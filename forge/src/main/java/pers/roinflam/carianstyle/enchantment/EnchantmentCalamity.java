package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 灾厄附魔（诅咒）
 * <p>
 * 受击伤害×1.5
 * 每tick 2%概率吸引32格内怪物
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - getTotalLevel() 中 getUsedItemHand() → InteractionHand.MAIN_HAND
 *   PlayerTickEvent 中玩家可能没有在"使用"物品，getUsedItemHand()返回值不确定
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "calamity",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        isCurse = true
)
@Mod.EventBusSubscriber
public class EnchantmentCalamity extends EnchantmentBase {

    public EnchantmentCalamity() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    /**
     * 获取护甲上的灾厄附魔总等级
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getArmorLevel(LivingEntity entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(calamity, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    /**
     * 获取所有装备（包括主手和护甲）的灾厄附魔总等级
     * <p>
     * v2.1修复：getUsedItemHand() → InteractionHand.MAIN_HAND
     * </p>
     *
     * @param entity 实体
     * @return 附魔总等级
     */
    private static int getTotalLevel(LivingEntity entity) {
        Enchantment calamity = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCalamity.class);
        if (calamity == null) {
            return 0;
        }

        int totalLevel = 0;

        // v2.1修复：使用主手而非 getUsedItemHand()
        ItemStack heldItem = entity.getItemInHand(InteractionHand.MAIN_HAND);
        if (!heldItem.isEmpty()) {
            totalLevel += EnchantmentHelper.getItemEnchantmentLevel(calamity, heldItem);
        }

        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(calamity, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    /**
     * 受击伤害增加50%
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        int totalLevel = getArmorLevel(victim);
        if (totalLevel > 0) {
            evt.setAmount(evt.getAmount() * 1.5f);
        }
    }

    /**
     * 每tick 2%概率吸引周围怪物
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide) {
            return;
        }

        if (evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (!RandomUtil.percentageChance(2)) {
            return;
        }

        Player player = evt.player;
        if (!player.isAlive()) {
            return;
        }

        int totalLevel = getTotalLevel(player);
        if (totalLevel <= 0) {
            return;
        }

        List<Mob> nearbyMobs = EntityUtil.getNearbyEntities(
                Mob.class,
                player,
                32
        );

        for (Mob mob : nearbyMobs) {
            LivingEntity currentTarget = mob.getTarget();

            if (currentTarget == null || !currentTarget.isAlive()) {
                if (RandomUtil.percentageChance(25)) {
                    mob.setTarget(player);
                }
            } else if (!currentTarget.equals(player)) {
                if (RandomUtil.percentageChance(50)) {
                    mob.setTarget(player);
                }
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
