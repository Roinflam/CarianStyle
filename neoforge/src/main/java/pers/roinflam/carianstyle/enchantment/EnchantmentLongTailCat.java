package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import pers.roinflam.carianstyle.event.damage.LivingAttackEvent;
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
 * 长尾猫附魔
 * <p>v2.1：LivingAttack摔落减伤入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "long_tail_cat",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantTarget.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        baseEnchantability = 30,
        levelMultiplier = 15
)
@EventBusSubscriber
public class EnchantmentLongTailCat extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.long_tail_cat.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "long_tail_cat";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 一级时的斩杀阈值（占目标最大生命的比例）
     * <p>默认 0.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle BASE_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "base_threshold",
                    0.5D, 0.0D, 10.0D);

    /**
     * 每超过一级增加的斩杀阈值比例
     * <p>默认 0.25，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle THRESHOLD_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "threshold_per_level",
                    0.25D, 0.0D, 10.0D);

    public EnchantmentLongTailCat() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!evt.getSource().getMsgId().equals("fall")) {
            return;
        }

        LivingEntity victim = evt.getEntity();

        // v2.1：怪物附魔触发开关（受击者视角，摔落保护非濒死触发）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        EnchantmentBase longTailCat = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLongTailCat.class);
        if (longTailCat == null) {
            return;
        }

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentLevels.get(longTailCat, armor);
            }
        }

        totalLevel = applyLevelLimit(longTailCat, totalLevel, LEVEL_CAP.getInt());

        if (totalLevel <= 0) {
            return;
        }

        float threshold = victim.getMaxHealth() * (float) BASE_THRESHOLD.get()
                + victim.getMaxHealth() * (totalLevel - 1) * (float) THRESHOLD_PER_LEVEL.get();

        if (evt.getAmount() < threshold) {
            evt.setCanceled(true);
        }
    }

    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.FEATHER_FALLING);
    }
}
