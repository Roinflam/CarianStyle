package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import pers.roinflam.carianstyle.event.damage.LivingHurtEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import java.util.UUID;
import net.neoforged.fml.common.EventBusSubscriber;

/**
 * PORT-STUB(E2)
 *
 * 米凯拉之刃附魔
 * <p>v2.2：攻击者+受击者计数器累积均接入怪物附魔触发开关</p>
 * <p>v2.3：攻击者一侧的叠层（加层、刷新过期、按层数增伤）要求玩家满蓄力；
 * 未满蓄力的一下只吃基础倍率，不加层也不吃层数加成，连点不会比正常节奏更强。受击者一侧不受影响</p>
 *
 * @version 2.3
 */
@Deprecated
public class EnchantmentMikaelaBlade extends EnchantmentBase {
    private static final String VALUE_ID = "mikaela_blade";
    private static final EnchantmentValues.Handle BASE_MULTIPLIER = EnchantmentValues.define(VALUE_ID, "base_multiplier",
                    0.4D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle MULTIPLIER_PER_COMBO = EnchantmentValues.define(VALUE_ID, "multiplier_per_combo",
                    0.2D, 0.0D, 5.0D);
    private static final EnchantmentValues.Handle COMBO_DURATION = EnchantmentValues.define(VALUE_ID, "combo_duration",
                    40, 1, 1200);
    private static final EnchantmentValues.Handle VICTIM_COMBO_BONUS = EnchantmentValues.define(VALUE_ID, "victim_combo_bonus",
                    0.1D, 0.0D, 5.0D);
    private static final String COMBO_COUNT_KEY = "mikaela_blade_combo";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

