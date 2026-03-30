package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;
import java.util.UUID;

/**
 * 重力附魔
 * <p>
 * 攻击时激活重力场，给敌人施加重力效果
 * 激活期间周围12格内其他生物持续受到轻微重力效果
 * </p>
 * <p>
 * 修复/优化记录：
 * - 修复getUsedItemHand → InteractionHand.MAIN_HAND
 * - 性能优化：将LivingTickEvent改为PlayerTickEvent，避免所有LivingEntity每tick都进入方法
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "gravitas",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentGravitas extends EnchantmentBase {

    private static final String GRAVITAS_ACTIVE_KEY = "gravitas_active";

    public EnchantmentGravitas() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 攻击时激活重力场并给敌人施加重力效果
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();

        // 修复：使用主手检查而非getUsedItemHand
        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment gravitas = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGravitas.class);
        if (gravitas == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(gravitas, heldItem);

        if (level <= 0) {
            return;
        }

        // 激活重力场状态
        int activeDuration = level * 4 * 20;
        EnchantmentDataManager.setCooldown(GRAVITAS_ACTIVE_KEY, attacker.getUUID(), activeDuration);

        // 给敌人施加重力效果
        int potionDuration = level * 2 * 20;
        int potionLevel = 10 + level * 4 - 1;
        victim.addEffect(new MobEffectInstance(
                CarianStylePotion.GRAVITAS.get(),
                potionDuration,
                potionLevel
        ));
    }

    /**
     * 死亡时清理状态
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }
        EnchantmentDataManager.clearCooldown(GRAVITAS_ACTIVE_KEY, evt.getEntity().getUUID());
    }

    /**
     * 激活状态时，周围生物持续受到轻微重力效果
     * <p>
     * 性能优化：改用PlayerTickEvent替代LivingTickEvent
     * 原代码注册在所有LivingEntity的tick上，每个存活实体每tick都会进入
     * 改为只对玩家检测，大幅减少无用调用
     * </p>
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        Player holder = evt.player;
        if (!holder.isAlive()) {
            return;
        }

        UUID uuid = holder.getUUID();
        if (!EnchantmentDataManager.isOnCooldown(GRAVITAS_ACTIVE_KEY, uuid)) {
            return;
        }

        // 周围12格内其他生物受到轻微重力效果
        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                holder,
                12,
                entity -> !entity.equals(holder)
        );

        for (LivingEntity entity : nearbyEntities) {
            entity.addEffect(new MobEffectInstance(CarianStylePotion.GRAVITAS.get(), 2, 9));
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.KNOCKBACK) {
            return false;
        }
        return super.checkCompatibility(ench);
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
