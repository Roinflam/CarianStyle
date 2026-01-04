package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 神圣秩序附魔
 *
 * 护甲附魔，吸收盾系统
 * 效果：
 * - 进入世界时获得100%最大生命值的吸收盾
 * - 击杀敌人时获得10%最大生命值的吸收盾（最多叠加到300%）
 * - 有吸收盾时受到伤害减少25%，并反弹5%吸收盾值的魔法伤害
 * - 有吸收盾时造成伤害增加50%
 * - 无法被治疗
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentSacredOrder extends EnchantmentBase {

    public EnchantmentSacredOrder() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 击杀敌人时获得吸收盾
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@Nonnull LivingDeathEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase killer = (EntityLivingBase) evt.getSource().getTrueSource();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : killer.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        // 注意：原代码没有等级上限检查
        if (totalLevel <= 0) {
            return;
        }

        // 吸收盾上限为300%最大生命值
        if (killer.getAbsorptionAmount() < killer.getMaxHealth() * 3) {
            float newAbsorption = Math.min(killer.getMaxHealth() * 3,
                    killer.getAbsorptionAmount() + killer.getMaxHealth() * 0.1f);
            killer.setAbsorptionAmount(newAbsorption);
        }
    }

    /**
     * 受伤时减伤并反弹，攻击时增伤
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@Nonnull LivingDamageEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        EntityLivingBase victim = evt.getEntityLiving();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        // 处理受击者（有吸收盾时减伤并反弹）
        if (victim.getAbsorptionAmount() > 0) {
            int victimLevel = 0;
            for (ItemStack armor : victim.getArmorInventoryList()) {
                if (!armor.isEmpty()) {
                    victimLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
                }
            }

            if (victimLevel > 0) {
                // 减伤25%
                evt.setAmount(evt.getAmount() * 0.75f);

                // 反弹5%吸收盾值的魔法伤害
                if (damageSource.getTrueSource() instanceof EntityLivingBase) {
                    EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();
                    attacker.attackEntityFrom(DamageSource.MAGIC, victim.getAbsorptionAmount() * 0.05f);
                }
            }
        }

        // 处理攻击者（有吸收盾时增伤）
        if (damageSource.getTrueSource() instanceof EntityLivingBase) {
            EntityLivingBase attacker = (EntityLivingBase) damageSource.getTrueSource();

            if (attacker.getAbsorptionAmount() > 0) {
                int attackerLevel = 0;
                for (ItemStack armor : attacker.getArmorInventoryList()) {
                    if (!armor.isEmpty()) {
                        attackerLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (attackerLevel > 0) {
                    // 增伤50%
                    evt.setAmount(evt.getAmount() * 1.5f);
                }
            }
        }
    }

    /**
     * 进入世界时获得吸收盾
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent evt) {
        if (evt.getWorld().isRemote) {
            return;
        }

        if (!(evt.getEntity() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase entity = (EntityLivingBase) evt.getEntity();

        // 已有吸收盾则跳过
        if (entity.getAbsorptionAmount() > 0) {
            return;
        }

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            entity.setAbsorptionAmount(entity.getMaxHealth());
        }
    }

    /**
     * 禁止治疗
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@Nonnull LivingHealEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityLivingBase entity = evt.getEntityLiving();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(Enchantment ench) {
        return super.canApplyTogether(ench) && !ench.equals(Enchantments.PROTECTION);
    }
}