package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 巨人火焰附魔
 * <p>
 * 着火时受击反弹50%伤害（火焰）
 * 减伤：伤害 × 血量比例 × 0.25
 * 免疫火焰伤害并转化为治疗（10tick冷却）
 * </p>
 * <p>v2.1：三个监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentGiantFlame extends EnchantmentBase {
    private static final String VALUE_ID = "giant_flame";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle REFLECT_RATIO = EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.5D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle REDUCTION_RATIO = EnchantmentValues.define(VALUE_ID, "reduction_ratio",
                    0.25D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle HEAL_COOLDOWN = EnchantmentValues.define(VALUE_ID, "heal_cooldown",
                    10, 1, 600);
    private static final String FLAME_HEAL_COOLDOWN_KEY = "giant_flame_heal_cooldown";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

