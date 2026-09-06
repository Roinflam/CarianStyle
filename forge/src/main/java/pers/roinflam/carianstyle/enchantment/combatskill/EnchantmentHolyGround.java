package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStyleEnchantments;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 圣地附魔
 * <p>v2.2：受击减伤光环入口接入怪物附魔触发开关。
 * PlayerTickEvent 仅玩家触发，无需开关检查。</p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "holy_ground",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        customType = "SHIELD",
        slots = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentHolyGround extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.holy_ground.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "holy_ground";

    /**
     * 每级减伤比例（上限受等级上限约束）
     * <p>默认 0.05，允许范围 0.0 ~ 0.2。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "damage_reduction_per_level",
                    0.05D, 0.0D, 0.2D);

    /**
     * 光环结算间隔（tick）
     * <p>默认 60，允许范围 5 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle PULSE_INTERVAL_TICKS =
            EnchantmentValues.define(VALUE_ID, "pulse_interval_ticks",
                    60, 5, 1200);

    /**
     * 每次结算每级回复的最大生命占比
     * <p>默认 0.015，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.015D, 0.0D, 0.5D);

    /**
     * 每次结算每级提供的伤害吸收占比
     * <p>默认 0.03，允许范围 0.0 ~ 0.5。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "absorption_per_level",
                    0.03D, 0.0D, 0.5D);

    public EnchantmentHolyGround() {
        super(CarianStyleEnchantments.getCustomEnchantmentCategory("SHIELD"), new EquipmentSlot[]{
                EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND
        });
    }

    /**
     * 减伤光环：附近有人举着此附魔盾牌时，受击者获得减伤
     */
    @SubscribeEvent
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // ⭐ v2.2：怪物附魔触发开关（受益者是受击者，怪物受益等同于触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                victim,
                16,
                entity -> entity.getClass() == victim.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            if (!entity.isUsingItem()) {
                continue;
            }

            ItemStack activeItem = entity.getItemInHand(entity.getUsedItemHand());
            if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
                continue;
            }

            // v-cache：走中央装备缓存。非玩家实体会自动回退到直接查询，行为不变
            int level = EnchantmentEventHandler.levelInHand(entity, holyGround, entity.getUsedItemHand());

            if (level > 0) {
                evt.setAmount(evt.getAmount() - evt.getAmount() * level * (float) DAMAGE_REDUCTION_PER_LEVEL.get());
            }
        }
    }

    /**
     * 治疗/护盾光环（PlayerTickEvent，玩家专属）
     */
    @SubscribeEvent
    public static void onPlayerTick(@NotNull TickEvent.PlayerTickEvent evt) {
        if (evt.player.level().isClientSide || evt.phase != TickEvent.Phase.START) {
            return;
        }

        if (evt.player.tickCount % PULSE_INTERVAL_TICKS.getInt() != 0) {
            return;
        }

        Player holder = evt.player;
        if (!holder.isAlive()) {
            return;
        }

        if (!holder.isUsingItem()) {
            return;
        }

        ItemStack activeItem = holder.getItemInHand(holder.getUsedItemHand());
        if (activeItem.isEmpty() || !(activeItem.getItem() instanceof ShieldItem)) {
            return;
        }

        Enchantment holyGround = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHolyGround.class);
        if (holyGround == null) {
            return;
        }

        // v-cache：走中央装备缓存
        int level = EnchantmentEventHandler.levelInHand(holder, holyGround, holder.getUsedItemHand());
        if (level <= 0) {
            return;
        }

        List<LivingEntity> nearbyEntities = EntityUtil.getNearbyEntities(
                LivingEntity.class,
                holder,
                16,
                entity -> entity.getClass() == holder.getClass()
        );

        for (LivingEntity entity : nearbyEntities) {
            boolean effectApplied = false;

            if (entity.getHealth() < entity.getMaxHealth()) {
                entity.heal(entity.getMaxHealth() * level * (float) HEAL_PER_LEVEL.get());
                effectApplied = true;
            }

            float maxAbsorption = entity.getMaxHealth() / 3 * level;
            if (entity.getAbsorptionAmount() < maxAbsorption) {
                float newAbsorption = Math.min(
                        entity.getAbsorptionAmount() + entity.getMaxHealth() * level * (float) ABSORPTION_PER_LEVEL.get(),
                        maxAbsorption
                );
                entity.setAbsorptionAmount(newAbsorption);
                effectApplied = true;
            }

            if (effectApplied) {
                entity.playSound(SoundEvents.PLAYER_LEVELUP, 1, 3);
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
