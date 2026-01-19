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
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;

/**
 * 黄金树的庇佑附魔
 * <p>
 * 护甲附魔，战斗时双方都能获得护盾
 * 受到攻击时：
 * - 受击者获得黄金树庇佑效果（持续 = 2.5 × 等级 秒，效果等级 = 附魔等级 - 1）
 * - 攻击者也获得黄金树庇佑效果（等级上限额外限制为5）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "protection_of_the_erdtree",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        conflictsWith = {Enchantments.class},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentProtectionOfTheErdtree extends EnchantmentBase {

    public EnchantmentProtectionOfTheErdtree() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
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

        Enchantment protection = EnchantmentRegistry.getEnchantmentByClass(EnchantmentProtectionOfTheErdtree.class);
        if (protection == null) {
            return;
        }

        // 处理受击者
        int victimLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                victimLevel += EnchantmentHelper.getItemEnchantmentLevel(protection, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            victimLevel = Math.min(victimLevel, 10);
        }

        if (victimLevel > 0) {
            int duration = (int) (2.5 * victimLevel * 20);
            victim.addEffect(new MobEffectInstance(
                    CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get(),
                    duration,
                    victimLevel - 1
            ));
        }

        // 处理攻击者
        int attackerLevel = 0;
        for (ItemStack armor : attacker.getArmorSlots()) {
            if (!armor.isEmpty()) {
                attackerLevel += EnchantmentHelper.getItemEnchantmentLevel(protection, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            attackerLevel = Math.min(attackerLevel, 10);
        }

        if (attackerLevel > 0) {
            // 攻击者等级额外限制为5
            attackerLevel = Math.min(attackerLevel, 5);

            int duration = (int) (2.5 * attackerLevel * 20);
            attacker.addEffect(new MobEffectInstance(
                    CarianStylePotion.PROTECTION_OF_THE_ERDTREE.get(),
                    duration,
                    attackerLevel - 1
            ));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}