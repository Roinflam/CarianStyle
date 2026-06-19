package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
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
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 托普斯之架附魔
 * <p>v2.1：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "topps_stand",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentToppsStand extends EnchantmentBase {

    public EnchantmentToppsStand() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!DamageSourceUtil.isMagicDamage(evt.getSource())) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment toppsStand = EnchantmentRegistry.getEnchantmentByClass(EnchantmentToppsStand.class);
        if (toppsStand == null) {
            return;
        }

        List<LivingEntity> entities = EntityUtil.getNearbyEntities(LivingEntity.class, victim, 6);

        if (evt.getSource().getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) evt.getSource().getEntity();
            entities.addAll(EntityUtil.getNearbyEntities(LivingEntity.class, attacker, 6));
        }

        for (LivingEntity entity : entities) {
            for (ItemStack armor : entity.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    if (EnchantmentHelper.getItemEnchantmentLevel(toppsStand, armor) > 0) {
                        evt.setCanceled(true);
                        return;
                    }
                }
            }
        }
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
