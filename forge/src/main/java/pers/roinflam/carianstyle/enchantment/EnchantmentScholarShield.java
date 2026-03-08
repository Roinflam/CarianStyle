package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
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
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;

/**
 * 学者盾附魔
 * <p>
 * 盾牌附魔，格挡时反伤和减伤
 * 格挡时：
 * - 对攻击者造成 10% × 等级 的魔法伤害（基于原伤害）
 * - 减少 7.5% × 等级 的伤害
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "scholar_shield",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentScholarShield extends EnchantmentBase {

    public EnchantmentScholarShield() {
        super(EnchantmentCategory.BREAKABLE, new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        if (!victim.isUsingItem()) {
            return;
        }

        ItemStack activeItem = victim.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(scholarShield, activeItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        attacker.hurt(
                attacker.damageSources().mobAttack(victim),
                evt.getAmount() * level * 0.1f
        );
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().getDirectEntity() == null) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        if (!victim.isUsingItem()) {
            return;
        }

        ItemStack activeItem = victim.getUseItem();
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment scholarShield = EnchantmentRegistry.getEnchantmentByClass(EnchantmentScholarShield.class);
        if (scholarShield == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(scholarShield, activeItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        evt.setAmount(evt.getAmount() - evt.getAmount() * level * 0.075f);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}