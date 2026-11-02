package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
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
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 洛蕾塔戏法附魔
 * <p>v2.2：ProjectileImpact射手视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@Deprecated
public class EnchantmentLorettaTrick extends EnchantmentBase {
    private static final String VALUE_ID = "loretta_trick";
    private static final EnchantmentValues.Handle ARROW_DAMAGE_PENALTY = EnchantmentValues.define(VALUE_ID, "arrow_damage_penalty",
                    0.25D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH = EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    3.0D, 0.0D, 20.0D);
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE = EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    4.0D, 0.0D, 20.0D);
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

