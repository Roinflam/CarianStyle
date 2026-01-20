package pers.roinflam.carianstyle.enchantment;

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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

/**
 * 洛蕾塔戏法附魔
 * <p>
 * 弓箭附魔，箭矢命中时连续爆炸
 * 效果：
 * - 箭矢伤害减少25%
 * - 延迟创建4次随机位置爆炸（着火箭威力4，普通箭威力3）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
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
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity attacker)) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaTrick = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaTrick.class);
        if (lorettaTrick == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaTrick, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 箭矢伤害减少25%
        arrow.setBaseDamage(arrow.getBaseDamage() - arrow.getBaseDamage() * 0.25);

        // 爆炸威力（着火箭威力4，普通箭威力3）
        float explosionStrength = arrow.getRemainingFireTicks() > 0 ? 4 : 3;

        // 延迟创建4次随机位置爆炸
        new SynchronizationTask(1, 5) {
            private int time = 0;

            @Override
            public void run() {
                if (++time > 4) {
                    this.cancel();
                    return;
                }

                // 在箭矢位置附近随机偏移
                double offsetX = -2.5 + RandomUtil.getInt(0, 5);
                double offsetZ = -2.5 + RandomUtil.getInt(0, 5);

                attacker.level().explode(
                        attacker,
                        arrow.getX() + offsetX,
                        arrow.getY(),
                        arrow.getZ() + offsetZ,
                        explosionStrength,
                        false,
                        net.minecraft.world.level.Level.ExplosionInteraction.NONE
                );
            }
        }.start();
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