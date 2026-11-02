package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.annotation.context.EnchantmentContext;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;

/**
 * PORT-STUB(E4)
 *
 * 催眠烟雾附魔
 * <p>
 * 武器附魔，概率使目标入睡
 * 攻击时：
 * - 2% × 等级的概率触发
 * - 延迟5tick后施加睡眠效果（持续 = 等级 × 3秒，效果等级 = 附魔等级 - 1）
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "hypnotic_smoke",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.UNCOMMON,
        type = EnchantTarget.WEAPON,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 30,
        levelMultiplier = 10
)
@Deprecated
public class EnchantmentHypnoticSmoke extends EnchantmentBase {
    private static final String VALUE_ID = "hypnotic_smoke";
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.0D, 0.0D, 100.0D);
    private static final EnchantmentValues.Handle APPLY_DELAY = EnchantmentValues.define(VALUE_ID, "apply_delay",
                    5, 0, 200);
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL = EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    3, 1, 120);
    public EnchantmentHypnoticSmoke() {
        super(EnchantTarget.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }
    protected void onDamageAsAttackerLowest(@NotNull EnchantmentContext ctx, int level) {
    }
    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench)
                && !ench.isOf(EnchantmentEpilepsyFire.class)
                && !ench.isOf(EnchantmentEatShit.class);
    }
}

