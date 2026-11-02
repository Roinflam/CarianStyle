package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E4)
 *
 * 神圣秩序附魔
 * <p>v2.1：onLivingDeath（击杀者）+ onLivingDamage（双向）+ onLivingHeal（受治疗者）入口
 * 接入怪物附魔触发开关。EntityJoinLevel是恢复初始吸收盾，属于状态恢复而非"触发"，无需检查。</p>
 * <p>v2.2：持盾减伤挪到 LivingHurtEvent。LivingDamageEvent 发出时原版已经扣完吸收盾了，
 * 盾够厚时事件里的伤害是 0，盾被打穿时盾量又是 0，这个减伤基本不生效。
 * 初始吸收盾改成每条命只给一次，以前没盾时切维度、重新登录就能反复刷满。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 35,
        levelMultiplier = 0
)
@Deprecated
public class EnchantmentSacredOrder extends EnchantmentBase {
    private static final String VALUE_ID = "sacred_order";
    private static final EnchantmentValues.Handle ABSORPTION_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "absorption_cap_ratio",
                    3.0D, 0.0D, 20.0D);
    private static final EnchantmentValues.Handle ABSORPTION_PER_KILL = EnchantmentValues.define(VALUE_ID, "absorption_per_kill",
                    0.1D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle SHIELDED_DAMAGE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "shielded_damage_multiplier",
                    0.75D, 0.0D, 1.0D);
    private static final EnchantmentValues.Handle REFLECT_RATIO = EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.05D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle VS_SHIELDED_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "vs_shielded_multiplier",
                    1.5D, 0.0D, 10.0D);
    private static final String INITIAL_SHIELD_GIVEN_KEY = "carianstyle_sacred_order_shield_given";
    public EnchantmentSacredOrder() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}

