package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
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
 * 绿龟附魔
 * <p>v2.1：LivingHeal受治疗者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "green_turtle",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 20,
        levelMultiplier = 10
)
@EventBusSubscriber
public class EnchantmentGreenTurtle extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.green_turtle.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "green_turtle";

    /**
     * 每级按治疗量提供的额外治疗比例
     * <p>默认 0.075，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle HEAL_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "heal_per_level",
                    0.075D, 0.0D, 2.0D);

    /**
     * 每级按已损失生命比例提供的额外治疗系数
     * <p>默认 0.15，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle MISSING_HEALTH_BONUS =
            EnchantmentValues.define(VALUE_ID, "missing_health_bonus",
                    0.15D, 0.0D, 5.0D);

    public EnchantmentGreenTurtle() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHeal(@NotNull LivingHealEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity entity = evt.getEntity();

        // v2.1：怪物附魔触发开关（受治疗者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        EnchantmentBase greenTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGreenTurtle.class);
        if (greenTurtle == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : entity.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentLevels.get(greenTurtle, armor);
            }
        }

        totalLevel = decayLevel(greenTurtle, totalLevel);

        if (totalLevel <= 0) {
            return;
        }

        float missingHealthPercent = 1 - entity.getHealth() / entity.getMaxHealth();

        float bonusHeal = evt.getAmount() * totalLevel * (float) HEAL_PER_LEVEL.get()
                + totalLevel * (float) MISSING_HEALTH_BONUS.get() * missingHealthPercent;

        evt.setAmount(evt.getAmount() + bonusHeal);
    }

    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench)
                && !ench.is(Enchantments.PROTECTION)
                && !ench.isOf(EnchantmentBlackFlameShelter.class);
    }
}
