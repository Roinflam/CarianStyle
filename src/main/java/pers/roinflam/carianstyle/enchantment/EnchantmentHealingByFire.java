package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import java.util.ArrayList;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 火焰疗愈附魔
 * <p>v2.2：LivingAttack受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@Deprecated
public class EnchantmentHealingByFire extends EnchantmentBase {
    private static final String VALUE_ID = "healing_by_fire";
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.5D, 0.0D, 100.0D);
    private static final EnchantmentValues.Handle ABSORPTION_RATIO = EnchantmentValues.define(VALUE_ID, "absorption_ratio",
                    0.1D, 0.0D, 2.0D);
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return false;
    }
}

