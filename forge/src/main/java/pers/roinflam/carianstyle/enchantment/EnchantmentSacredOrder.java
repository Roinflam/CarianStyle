package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 神圣秩序附魔
 * <p>v2.1：onLivingDeath（击杀者）+ onLivingDamage（双向）+ onLivingHeal（受治疗者）入口
 * 接入怪物附魔触发开关。EntityJoinLevel是恢复初始吸收盾，属于状态恢复而非"触发"，无需检查。</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "sacred_order",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}
)
@Mod.EventBusSubscriber
public class EnchantmentSacredOrder extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.sacred_order.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sacred_order";

    /**
     * 伤害吸收上限相对最大生命的倍数
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_CAP_RATIO =
            EnchantmentValues.define(VALUE_ID, "absorption_cap_ratio",
                    3.0D, 0.0D, 20.0D);

    /**
     * 每次击杀获得的伤害吸收占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_PER_KILL =
            EnchantmentValues.define(VALUE_ID, "absorption_per_kill",
                    0.1D, 0.0D, 2.0D);

    /**
     * 自身有吸收护盾时受到伤害的倍率
     * <p>默认 0.75，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SHIELDED_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "shielded_damage_multiplier",
                    0.75D, 0.0D, 1.0D);

    /**
     * 按目标剩余吸收量反弹给攻击者的伤害比例
     * <p>默认 0.05，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle REFLECT_RATIO =
            EnchantmentValues.define(VALUE_ID, "reflect_ratio",
                    0.05D, 0.0D, 2.0D);

    /**
     * 攻击带吸收护盾的目标时的伤害倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle VS_SHIELDED_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "vs_shielded_multiplier",
                    1.5D, 0.0D, 10.0D);

    public EnchantmentSacredOrder() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity killer = (LivingEntity) evt.getSource().getEntity();

        // ⭐ v2.1：怪物附魔触发开关（击杀者视角，击杀加吸收盾非濒死）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(killer, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : killer.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel <= 0) {
            return;
        }

        if (killer.getAbsorptionAmount() < killer.getMaxHealth() * (float) ABSORPTION_CAP_RATIO.get()) {
            float newAbsorption = Math.min(killer.getMaxHealth() * (float) ABSORPTION_CAP_RATIO.get(),
                    killer.getAbsorptionAmount() + killer.getMaxHealth() * (float) ABSORPTION_PER_KILL.get());
            killer.setAbsorptionAmount(newAbsorption);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        LivingEntity victim = evt.getEntity();

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        // 受击者视角（吸收盾减伤+反弹）
        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            if (victim.getAbsorptionAmount() > 0) {
                int victimLevel = 0;
                for (ItemStack armor : victim.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        victimLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (victimLevel > 0) {
                    evt.setAmount(evt.getAmount() * (float) SHIELDED_DAMAGE_MULTIPLIER.get());

                    if (damageSource.getEntity() instanceof LivingEntity) {
                        LivingEntity attacker = (LivingEntity) damageSource.getEntity();
                        attacker.hurt(attacker.damageSources().magic(), victim.getAbsorptionAmount() * (float) REFLECT_RATIO.get());
                    }
                }
            }
        }

        // 攻击者视角（持有吸收盾时增伤）
        if (damageSource.getEntity() instanceof LivingEntity) {
            LivingEntity attacker = (LivingEntity) damageSource.getEntity();

            // ⭐ v2.1：怪物附魔触发开关（攻击者视角）
            if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

            if (attacker.getAbsorptionAmount() > 0) {
                int attackerLevel = 0;
                for (ItemStack armor : attacker.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        attackerLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
                    }
                }

                if (attackerLevel > 0) {
                    evt.setAmount(evt.getAmount() * (float) VS_SHIELDED_MULTIPLIER.get());
                }
            }
        }
    }

    /**
     * 实体进入世界时获得初始吸收盾。
     * 此事件属于状态恢复（玩家重连等），未接入怪物附魔开关。
     * 但若有"启用开关时怪物已经获得过盾"的情况，无法回收，这是设计权衡。
     */
    @SubscribeEvent
    public static void onEntityJoinWorld(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide()) {
            return;
        }

        if (!(evt.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity entity = (LivingEntity) evt.getEntity();

        if (entity.getAbsorptionAmount() > 0) {
            return;
        }

        // ⭐ v2.1：状态恢复也加开关检查，避免新生怪物获得吸收盾
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            entity.setAbsorptionAmount(entity.getMaxHealth());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity entity = evt.getEntity();

        // ⭐ v2.1：怪物附魔触发开关（受治疗者视角，"无法被治疗"也属于附魔触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        Enchantment sacredOrder = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSacredOrder.class);
        if (sacredOrder == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(sacredOrder, armor);
            }
        }

        if (totalLevel > 0) {
            evt.setCanceled(true);
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
