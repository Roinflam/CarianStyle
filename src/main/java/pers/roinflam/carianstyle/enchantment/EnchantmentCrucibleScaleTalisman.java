package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingDamageEvent;
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

/**
 * 坩埚鳞片护符附魔
 * <p>v2.1：LivingDamage受击者致命减伤入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "crucible_scale_talisman",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 20,
        levelMultiplier = 15
)
@EventBusSubscriber
public class EnchantmentCrucibleScaleTalisman extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.crucible_scale_talisman.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "crucible_scale_talisman";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 参与减伤计算的等级上限（低于总等级上限）
     * <p>默认 6，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle EFFECTIVE_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "effective_level_cap",
                    6, 1, 100);

    /**
     * 每级的减伤比例
     * <p>默认 0.15，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle REDUCTION_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "reduction_per_level",
                    0.15D, 0.0D, 1.0D);

    public EnchantmentCrucibleScaleTalisman() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        if (evt.getAmount() < victim.getMaxHealth()) {
            return;
        }

        // v2.1：怪物附魔触发开关（受击者视角，致命伤害减伤）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        EnchantmentBase crucibleScaleTalisman = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCrucibleScaleTalisman.class);
        if (crucibleScaleTalisman == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentLevels.get(crucibleScaleTalisman, armor);
            }
        }

        totalLevel = applyLevelLimit(crucibleScaleTalisman, totalLevel, LEVEL_CAP.getInt());

        if (totalLevel <= 0) {
            return;
        }

        float reduction = evt.getAmount() * Math.min(totalLevel, EFFECTIVE_LEVEL_CAP.getInt())
                * (float) REDUCTION_PER_LEVEL.get();
        evt.setAmount(evt.getAmount() - reduction);
    }

}
