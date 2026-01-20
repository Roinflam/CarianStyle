package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
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

/**
 * 洛蕾塔大弓附魔
 * <p>
 * 弓箭附魔，箭矢命中时爆炸
 * 效果：
 * - 箭矢伤害增加50%
 * - 在箭矢位置创建爆炸（着火箭威力3，普通箭威力2）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "loretta_big_bow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    public EnchantmentLorettaBigBow() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢发射时增加伤害
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onArrowJoinWorld(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide()) {
            return;
        }

        if (!(evt.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity attacker)) {
            return;
        }

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaBigBow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level > 0) {
            // 箭矢伤害增加50%
            arrow.setBaseDamage(arrow.getBaseDamage() * 1.5);
        }
    }

    /**
     * 箭矢命中时爆炸
     */
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

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaBigBow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 创建爆炸（着火箭威力3，普通箭威力2）
        float explosionStrength = arrow.getRemainingFireTicks() > 0 ? 3 : 2;
        attacker.level().explode(
                attacker,
                arrow.getX(),
                arrow.getY(),
                arrow.getZ(),
                explosionStrength,
                false,
                net.minecraft.world.level.Level.ExplosionInteraction.NONE
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (25 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}