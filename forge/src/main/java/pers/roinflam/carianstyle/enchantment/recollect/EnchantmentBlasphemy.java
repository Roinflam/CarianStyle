package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 亵渎附魔
 * <p>v2.2：onLivingDeath 入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "blasphemy", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT, rarity = EnchantmentRarity.VERY_RARE, type = EnchantmentCategory.WEAPON, slots = {EquipmentSlot.MAINHAND})
@Mod.EventBusSubscriber
public class EnchantmentBlasphemy extends EnchantmentBase {
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public EnchantmentBlasphemy() { super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity killer)) return;
        LivingEntity dead = evt.getEntity();
        if (!killer.isAlive() || dead.equals(killer)) return;

        // ⭐ v2.2：怪物附魔触发开关（击杀者视角，击杀奖励非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        ItemStack heldItem = killer.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment blasphemy = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlasphemy.class);
        if (blasphemy == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(blasphemy, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;
        killer.heal(dead.getMaxHealth() * 0.1f);
        if (killer instanceof Player player) {
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(Math.min(foodData.getFoodLevel() + 2, 20));
        }
    }

    @Override public int getMinCost(int l) { return (int)(RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty); }
    @Override public int getMaxCost(int l) { return getMinCost(l) + 50; }
}
