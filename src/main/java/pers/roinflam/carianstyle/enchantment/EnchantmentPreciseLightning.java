package pers.roinflam.carianstyle.enchantment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.apache.commons.lang3.RandomUtils;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 精准落雷附魔
 * <p>v2.2：LivingDamage受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "precise_lightning", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.RARE, type = EnchantTarget.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, conflictsWith = {EnchantmentCausalityPrinciple.class}, baseEnchantability = 30, levelMultiplier = 10)
@Deprecated
public class EnchantmentPreciseLightning extends EnchantmentBase {
    private static final String VALUE_ID = "precise_lightning";
    private static final EnchantmentValues.Handle DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.3D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle KNOCKBACK_STRENGTH = EnchantmentValues.define(VALUE_ID, "knockback_strength",
                    0.2D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle MAGNIFICATION_HIGH = EnchantmentValues.define(VALUE_ID, "magnification_high",
                    4, 1, 100);
    private static final EnchantmentValues.Handle MAGNIFICATION_MID = EnchantmentValues.define(VALUE_ID, "magnification_mid",
                    2, 1, 100);
    public EnchantmentPreciseLightning() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }
}

