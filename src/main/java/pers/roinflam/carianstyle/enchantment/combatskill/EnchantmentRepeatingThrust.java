package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.UUID;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E1)
 *
 * 连击附魔
 * <p>v2.2：onDamageAsAttackerHighest 走中央事件分发器，已被 scanEntity 拦截。
 * onLivingDeath 是清理逻辑（清除目标死亡时的连击叠层），不影响触发行为，无需开关。</p>
 * <p>v2.3：加层与增伤要求玩家满蓄力；换目标清层、击杀清理的逻辑不变。</p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "repeating_thrust",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 5,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentRepeatingThrust extends EnchantmentBase {
    private static final String VALUE_ID = "repeating_thrust";
    private static final EnchantmentValues.Handle LEVEL_CAP = EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);
    private static final EnchantmentValues.Handle DAMAGE_PER_STACK_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "damage_per_stack_per_level",
                    0.05D, 0.0D, 2.0D);
    private static final String CURRENT_TARGET_KEY = "repeating_thrust_target";
    private static final String STACK_COUNT_KEY = "repeating_thrust_stacks";
    private static final int STACK_DURATION = 200;
    public EnchantmentRepeatingThrust() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerHighest(@NotNull EnchantmentContext ctx, int level) {
    }
}

