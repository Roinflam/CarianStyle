package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;
import java.util.List;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * PORT-STUB(E4)
 *
 * 诺克斯之月附魔
 * <p>
 * 性能优化：从ServerTickEvent遍历所有世界所有实体改为PlayerTickEvent
 * 原代码每秒遍历服务器所有维度的所有实体，性能开销极大
 * 优化后：只在有附魔的玩家周围搜索锁定自己的怪物
 * </p>
 * @version 2.1
 */
@Deprecated
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {
    private static final String VALUE_ID = "moon_of_noxtura";
    private static final EnchantmentValues.Handle TICK_INTERVAL = EnchantmentValues.define(VALUE_ID, "tick_interval",
                    20, 1, 600);
    private static final EnchantmentValues.Handle TRIGGER_CHANCE = EnchantmentValues.define(VALUE_ID, "trigger_chance",
                    2.5D, 0.0D, 100.0D);
    private static final EnchantmentValues.Handle SEARCH_RADIUS = EnchantmentValues.define(VALUE_ID, "search_radius",
                    32, 1, 64);
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

