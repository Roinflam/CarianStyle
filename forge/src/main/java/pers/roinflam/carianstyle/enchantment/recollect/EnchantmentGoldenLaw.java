package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import java.util.UUID;

/**
 * 黄金律法附魔
 * <p>
 * 攻击：增伤15%~60%（血量越低越高）
 * 受击：减伤15%
 * 免疫：伤害<=血量15%直接免疫，否则100tick冷却内免疫一次
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "golden_law",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.RECOLLECT,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentGoldenLaw extends EnchantmentBase {

    private static final String IMMUNITY_COOLDOWN_KEY = "golden_law_immunity";
    private static final int RECOLLECT_ENCHANTABILITY = 35;

    public EnchantmentGoldenLaw() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) {
            return;
        }

        // 攻击者视角：增伤
        if (evt.getSource().getDirectEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

            ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
            if (!heldItem.isEmpty()) {
                int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);

                if (level > 0) {
                    float healthRatio = attacker.getHealth() / attacker.getMaxHealth();
                    float bonusDamage = evt.getAmount() * 0.15f + evt.getAmount() * 0.45f * (1 - healthRatio);
                    evt.setAmount(evt.getAmount() + bonusDamage);
                }
            }
        }

        // 受击者视角：减伤15%
        LivingEntity victim = evt.getEntity();

        ItemStack heldItem = victim.getItemInHand(victim.getUsedItemHand());
        if (!heldItem.isEmpty()) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, heldItem);

            if (level > 0) {
                evt.setAmount(evt.getAmount() * 0.85f);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().isCreativePlayer()) {
            return;
        }

        LivingEntity holder = evt.getEntity();
        UUID uuid = holder.getUUID();

        ItemStack mainHand = holder.getMainHandItem();
        if (mainHand.isEmpty()) {
            return;
        }

        Enchantment goldenLaw = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenLaw.class);
        if (goldenLaw == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(goldenLaw, mainHand);

        if (level <= 0) {
            return;
        }

        if (evt.getAmount() <= holder.getHealth() * 0.15) {
            evt.setCanceled(true);
            return;
        }

        if (!EnchantmentDataManager.isOnCooldown(IMMUNITY_COOLDOWN_KEY, uuid)) {
            evt.setCanceled(true);
            EnchantmentDataManager.setCooldown(IMMUNITY_COOLDOWN_KEY, uuid, 100);
        }
    }

    @Override
    protected boolean checkCompatibility(Enchantment ench) {
        if (isLawEnchantment(ench) && !ench.equals(this)) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    private boolean isLawEnchantment(Enchantment ench) {
        return ench.getClass().getPackage().getName().contains("law");
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