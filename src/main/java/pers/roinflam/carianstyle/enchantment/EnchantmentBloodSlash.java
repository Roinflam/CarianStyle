package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.recollect.EnchantmentTimeReversal;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E3)
 *
 * 血斩附魔
 * <p>v2.2：onLivingDeath 击杀者视角入口接入怪物附魔触发开关。
 * onHurtAsAttackerLow 走中央事件分发器，已被 scanEntity 拦截。</p>
 *
 * @version 2.2
 */
@Deprecated
public class EnchantmentBloodSlash extends EnchantmentBase {
    private static final String VALUE_ID = "blood_slash";
    private static final EnchantmentValues.Handle BONUS_DAMAGE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "bonus_damage_per_level",
                    0.05D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.1D, 0.0D, 0.9D);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL_WITH_COLLECTION = EnchantmentValues.define(VALUE_ID, "heal_per_level_with_collection",
                    0.05D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.025D, 0.0D, 2.0D);
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

