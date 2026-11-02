package pers.roinflam.carianstyle.enchantment.recollect;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodCollection;
import pers.roinflam.carianstyle.enchantment.EnchantmentBloodSlash;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.data.EnchantmentDataManager;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;
import java.util.UUID;

/**
 * PORT-STUB(E2)
 * 血附魔 - 修复: getUsedItemHand -> InteractionHand.MAIN_HAND @version 2.1 */
@Deprecated
public class EnchantmentBlood extends EnchantmentBase {
    private static final String VALUE_ID = "blood";
    private static final EnchantmentValues.Handle TRIGGER_ATTACK_COUNT = EnchantmentValues.define(VALUE_ID, "trigger_attack_count",
                    2, 1, 50);
    private static final EnchantmentValues.Handle DAMAGE_RATIO = EnchantmentValues.define(VALUE_ID, "damage_ratio",
                    0.12D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEAL_CAP_RATIO = EnchantmentValues.define(VALUE_ID, "heal_cap_ratio",
                    0.18D, 0.0D, 2.0D);
    private static final EnchantmentValues.Handle HEMORRHAGE_LEVEL_COMBO = EnchantmentValues.define(VALUE_ID, "hemorrhage_level_combo",
                    7, 0, 127);
    private static final EnchantmentValues.Handle HEMORRHAGE_DURATION = EnchantmentValues.define(VALUE_ID, "hemorrhage_duration",
                    30, 1, 1200);
    private static final EnchantmentValues.Handle COUNTER_DURATION = EnchantmentValues.define(VALUE_ID, "counter_duration",
                    6000, 20, 72000);
    private static final String ATTACK_COUNT_KEY = "blood_attack_count";
    private static final int RECOLLECT_ENCHANTABILITY = 35;
    protected void onHurtAsAttackerLow(@NotNull EnchantmentContext ctx, int level) {
    }
    public int getMinCost(int l) {
        return 0;
    }
    public int getMaxCost(int l) {
        return 0;
    }
}

