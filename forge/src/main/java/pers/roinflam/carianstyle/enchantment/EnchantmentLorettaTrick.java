package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 洛蕾塔戏法附魔
 * <p>v2.2：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "loretta_trick",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {EnchantmentLorettaBigBow.class},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentLorettaTrick extends EnchantmentBase {
    public EnchantmentLorettaTrick() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof LivingEntity attacker)) return;

        // ⭐ v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        Enchantment lorettaTrick = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaTrick.class);
        if (lorettaTrick == null) return;
        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaTrick, heldItem);
        if (ConfigLoader.levelLimit) level = Math.min(level, 10);
        if (level <= 0) return;

        arrow.setBaseDamage(arrow.getBaseDamage() - arrow.getBaseDamage() * 0.25);
        float explosionStrength = arrow.getRemainingFireTicks() > 0 ? 4 : 3;
        new SynchronizationTask(1, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > 4) {
                    this.cancel();
                    return;
                }
                double offsetX = -2.5 + RandomUtil.getInt(0, 5);
                double offsetZ = -2.5 + RandomUtil.getInt(0, 5);
                attacker.level().explode(attacker, arrow.getX() + offsetX, arrow.getY(), arrow.getZ() + offsetZ,
                        explosionStrength, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
            }
        }.start();
    }

    @Override
    public int getMinCost(int l) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int l) {
        return getMinCost(l) + 50;
    }
}
