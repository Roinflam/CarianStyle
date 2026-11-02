package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/** 黄金粪金龟附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@AutoRegisterEnchantment(id = "golden_dung_turtle", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantTarget.WEAPON, slots = {EquipmentSlot.MAINHAND}, baseEnchantability = 5, levelMultiplier = 10)
@EventBusSubscriber
public class EnchantmentGoldenDungTurtle extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.golden_dung_turtle.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_dung_turtle";

    /**
     * 每级额外掉落的经验比例
     * <p>默认 0.3，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle EXP_BONUS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "exp_bonus_per_level",
                    0.3D, 0.0D, 10.0D);

    public EnchantmentGoldenDungTurtle() { super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND}); }

    @SubscribeEvent
    public static void onLivingExperienceDrop(@NotNull LivingExperienceDropEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        Player player = evt.getAttackingPlayer();
        if (player == null) return;
        // 修复：使用主手
        ItemStack heldItem = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) return;
        EnchantmentBase goldenDungTurtle = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenDungTurtle.class);
        if (goldenDungTurtle == null) return;
        int level = EnchantmentLevels.get(goldenDungTurtle, heldItem);
        level = applyLevelLimit(goldenDungTurtle, level, 10);
        if (level <= 0) return;
        evt.setDroppedExperience(evt.getDroppedExperience() + (int) (evt.getDroppedExperience() * level * EXP_BONUS_PER_LEVEL.get()));
    }

}
