package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
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
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.UUID;

/**
 * 巨人火焰附魔
 * <p>
 * 着火时受击反弹50%伤害（火焰）
 * 减伤：伤害 × 血量比例 × 0.25
 * 免疫火焰伤害并转化为治疗（10tick冷却）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "giant_flame",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST}
)
@Mod.EventBusSubscriber
public class EnchantmentGiantFlame extends EnchantmentBase {

    private static final String FLAME_HEAL_COOLDOWN_KEY = "giant_flame_heal_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGiantFlame() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    private static int getTotalLevel(LivingEntity entity) {
        Enchantment giantFlame = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGiantFlame.class);
        if (giantFlame == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(giantFlame, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        if (holder.getRemainingFireTicks() <= 0) {
            return;
        }

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        attacker.hurt(holder.damageSources().inFire(), evt.getAmount() * 0.5f);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        float healthRatio = holder.getHealth() / holder.getMaxHealth();
        evt.setAmount(evt.getAmount() - evt.getAmount() * healthRatio * 0.25f);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!evt.getEntity().isAlive()) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        int totalLevel = getTotalLevel(holder);
        if (totalLevel <= 0) {
            return;
        }

        if (!DamageSourceUtil.isFireDamage(evt.getSource())) {
            return;
        }

        evt.setCanceled(true);

        if (!EnchantmentDataManager.isOnCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid)) {
            holder.heal(evt.getAmount());
            EnchantmentDataManager.setCooldown(FLAME_HEAL_COOLDOWN_KEY, uuid, 10);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (RECOLLECT_ENCHANTABILITY * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}