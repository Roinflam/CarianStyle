package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
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
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E4)
 *
 * 洛蕾塔大弓附魔
 * <p>v2.1：EntityJoinLevel箭矢生成+ProjectileImpact命中两个入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@Deprecated
public class EnchantmentLorettaBigBow extends EnchantmentBase {
    private static final String VALUE_ID = "loretta_big_bow";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    1.5D, 0.0D, 10.0D);
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH = EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    2.0D, 0.0D, 20.0D);
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE = EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    3.0D, 0.0D, 20.0D);
    static void spawnBlastCloud(Level level, double x, double y, double z) {
    }
    public int getMinCost(int enchantmentLevel) {
        return 0;
    }
    public int getMaxCost(int enchantmentLevel) {
        return 0;
    }
}

