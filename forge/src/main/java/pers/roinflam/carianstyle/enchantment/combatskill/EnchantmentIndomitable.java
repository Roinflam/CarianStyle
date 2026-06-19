package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

/**
 * 不屈附魔
 * <p>v2.1：受击者视角接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "indomitable",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentIndomitable extends EnchantmentBase {

    public EnchantmentIndomitable() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (evt.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }

        LivingEntity holder = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受击者视角，低血量伤害免疫）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(holder, false)) return;

        Enchantment indomitable = EnchantmentRegistry.getEnchantmentByClass(EnchantmentIndomitable.class);
        if (indomitable == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : holder.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(indomitable, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - holder.getHealth() / holder.getMaxHealth();
        double immuneChance = missingHealthPercent * 100 * 0.75;

        if (RandomUtil.percentageChance(immuneChance)) {
            evt.setCanceled(true);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.ALL_DAMAGE_PROTECTION ||
                ench == Enchantments.FIRE_PROTECTION ||
                ench == Enchantments.PROJECTILE_PROTECTION ||
                ench == Enchantments.BLAST_PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (30 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
