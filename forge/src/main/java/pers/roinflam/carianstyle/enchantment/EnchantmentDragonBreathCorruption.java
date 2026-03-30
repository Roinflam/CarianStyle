package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 龙息腐败附魔
 * <p>
 * 弓箭附魔，箭矢落地时对范围内敌人施加猩红腐败
 * 范围 = 等级 × 2格
 * 猩红腐败：持续时间 = 等级 × 5秒，等级 = 附魔等级 - 1
 * </p>
 * <p>
 * 修复记录 v2.1：
 * - getUsedItemHand() → InteractionHand.MAIN_HAND
 *   箭矢落地时玩家已放开弓，不再处于"使用物品"状态
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "dragon_breath_corruption",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentDragonBreathCorruption extends EnchantmentBase {

    public EnchantmentDragonBreathCorruption() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.getOwner() == null || evt.getRayTraceResult().getType() == net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity attacker)) {
            return;
        }

        // v2.1修复：使用主手而非 getUsedItemHand()
        // 箭矢落地时玩家已放开弓
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment dragonBreath = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragonBreathCorruption.class);
        if (dragonBreath == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(dragonBreath, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        List<LivingEntity> targets = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                arrow,
                level * 2
        );

        for (LivingEntity target : targets) {
            target.addEffect(new MobEffectInstance(
                    CarianStylePotion.SCARLET_ROT.get(),
                    level * 5 * 20,
                    level - 1
            ));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
