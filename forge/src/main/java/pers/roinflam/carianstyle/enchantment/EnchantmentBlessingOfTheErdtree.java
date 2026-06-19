package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 黄金树祝福附魔
 * <p>v2.1：LivingAttack双向监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "blessing_of_the_erdtree",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR_CHEST,
        slots = {EquipmentSlot.CHEST},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentBlessingOfTheErdtree extends EnchantmentBase {

    public EnchantmentBlessingOfTheErdtree() {
        super(EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{EquipmentSlot.CHEST});
    }

    private static int getTotalLevel(LivingEntity entity) {
        Enchantment blessingOfTheErdtree = EnchantmentRegistry.getEnchantmentByClass(EnchantmentBlessingOfTheErdtree.class);
        if (blessingOfTheErdtree == null) {
            return 0;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(blessingOfTheErdtree, armor);
            }
        }
        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }
        return totalLevel;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        // 受击者视角
        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            int victimLevel = getTotalLevel(victim);
            if (victimLevel > 0) {
                int duration = (int) (2.5 * victimLevel * 20);
                int amplifier = victimLevel - 1;
                victim.addEffect(new MobEffectInstance(CarianStylePotion.BLESSING_OF_THE_ERDTREE.get(), duration, amplifier));
            }
        }

        // 攻击者视角
        // ⭐ v2.1：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        int attackerLevel = getTotalLevel(attacker);
        if (attackerLevel > 0) {
            attackerLevel = Math.min(attackerLevel, 5);
            int duration = (int) (2.5 * attackerLevel * 20);
            int amplifier = attackerLevel - 1;
            attacker.addEffect(new MobEffectInstance(CarianStylePotion.BLESSING_OF_THE_ERDTREE.get(), duration, amplifier));
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        if (ench == Enchantments.ALL_DAMAGE_PROTECTION) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
