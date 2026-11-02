package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.effect.MobEffectInstance;
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
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentLevels;
import net.neoforged.fml.common.EventBusSubscriber;
import pers.roinflam.carianstyle.base.enchantment.EnchantKey;
import pers.roinflam.carianstyle.base.entity.AbsorptionShield;

/**
 * 火焰疗愈附魔
 * <p>v2.2：LivingAttack受击者视角入口接入怪物附魔触发开关</p>
 *
 * @version 2.2
 */
@AutoRegisterEnchantment(id = "healing_by_fire", category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL, rarity = EnchantmentRarity.UNCOMMON, type = EnchantTarget.ARMOR, slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}, baseEnchantability = 20, levelMultiplier = 5)
@EventBusSubscriber
public class EnchantmentHealingByFire extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.healing_by_fire.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "healing_by_fire";

    /**
     * 每级的触发概率（百分比）
     * <p>默认 2.5，允许范围 0.0 ~ 100.0。</p>
     */
    private static final EnchantmentValues.Handle CHANCE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "chance_per_level",
                    2.5D, 0.0D, 100.0D);

    /**
     * 触发时获得的伤害吸收占最大生命的比例
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle ABSORPTION_RATIO =
            EnchantmentValues.define(VALUE_ID, "absorption_ratio",
                    0.1D, 0.0D, 2.0D);

    public EnchantmentHealingByFire() {
        super(EnchantTarget.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) return;
        if (!(evt.getSource().getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = evt.getEntity();

        // v2.2：怪物附魔触发开关（受击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) return;

        if (victim.getRemainingFireTicks() <= 0) return;
        if (victim.getActiveEffects().isEmpty()) return;

        EnchantmentBase healingByFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class);
        if (healingByFire == null) return;

        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorSlots()) {
            if (!armor.isEmpty()) totalLevel += EnchantmentLevels.get(healingByFire, armor);
        }
        totalLevel = applyLevelLimit(healingByFire, totalLevel, 10);
        if (totalLevel <= 0) return;
        if (!RandomUtil.percentageChance(totalLevel * CHANCE_PER_LEVEL.get())) return;

        List<MobEffectInstance> badEffects = new ArrayList<>(victim.getActiveEffects());
        badEffects.removeIf(effect ->
                effect.getEffect().value().isBeneficial() ||
                        effect.getEffect().value().isInstantenous() ||
                        !effect.isVisible()
        );

        if (badEffects.isEmpty()) return;

        MobEffectInstance toRemove = badEffects.get(RandomUtil.getInt(0, badEffects.size() - 1));
        victim.removeEffect(toRemove.getEffect());
        // 1.21.1 吸收值被 max_absorption 夹住，走 AbsorptionShield 才加得上
        AbsorptionShield.grant(victim, victim.getAbsorptionAmount()
                + victim.getMaxHealth() * (float) ABSORPTION_RATIO.get());
    }

    @Override
    protected boolean checkCompatibility(@NotNull EnchantKey ench) {
        return super.checkCompatibility(ench) && !ench.is(Enchantments.PROTECTION);
    }
}
