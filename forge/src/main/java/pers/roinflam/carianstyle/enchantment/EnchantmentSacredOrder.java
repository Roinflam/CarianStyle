package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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

/**
 * 神圣秩序附魔
 * <p>v2.1：onLivingDeath（击杀者）+ onLivingDamage（双向）+ onLivingHeal（受治疗者）入口
 * 接入怪物附魔触发开关。EntityJoinLevel是恢复初始吸收盾，属于状态恢复而非"触发"，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentSacredOrder extends EnchantmentBase {

    public EnchantmentSacredOrder() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity killer = (LivingEntity) evt.getSource().getEntity();

        // ⭐ v2.1：怪物附魔触发开关（击杀者视角，击杀加吸收盾非濒死）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : killer.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (killer.getAbsorptionAmount() < killer.getMaxHealth() * 3) {
            float newAbsorption = Math.min(killer.getMaxHealth() * 3,
                    killer.getAbsorptionAmount() + killer.getMaxHealth() * 0.1f);
            killer.setAbsorptionAmount(newAbsorption);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        LivingEntity victim = evt.getEntity();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        // 受击者视角（吸收盾减伤+反弹）
        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            if (victim.getAbsorptionAmount() > 0) {
                int victimLevel = 0;
                for (ItemStack armor : victim.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        victimLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (victimLevel > 0) {
                    evt.setAmount(evt.getAmount() * 0.75f);

                    if (damageSource.getEntity() instanceof LivingEntity) {
                        LivingEntity attacker = (LivingEntity) damageSource.getEntity();
                        attacker.hurt(attacker.damageSources().magic(), victim.getAbsorptionAmount() * 0.05f);
                    }
                }
            }
        }

        // 攻击者视角（持有吸收盾时增伤）
        if (damageSource.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) damageSource.getEntity();

            // ⭐ v2.1：怪物附魔触发开关（攻击者视角）
            if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

            if (attacker.getAbsorptionAmount() > 0) {
                int attackerLevel = 0;
                for (ItemStack armor : attacker.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        attackerLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (attackerLevel > 0) {
                    evt.setAmount(evt.getAmount() * 1.5f);
                }
            }
        }
    }

    /**
     * 实体进入世界时获得初始吸收盾。
     * 此事件属于状态恢复（玩家重连等），未接入怪物附魔开关。
     * 但若有"启用开关时怪物已经获得过盾"的情况，无法回收，这是设计权衡。
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide()) {
            return;
        }

        if (!(evt.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) evt.getEntity();

        if (entity.getAbsorptionAmount() > 0) {
            return;
        }

        // ⭐ v2.1：状态恢复也加开关检查，避免新生怪物获得吸收盾
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            entity.setAbsorptionAmount(entity.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity entity = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受治疗者视角，"无法被治疗"也属于附魔触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
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
