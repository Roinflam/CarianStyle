package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 黄金誓约附魔
 * <p>v2.1：LivingAttack双向监听器入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "golden_vow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentGoldenVow extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.golden_vow.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "golden_vow";

    /**
     * 受击方参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "victim_level_cap",
                    10, 1, 100);

    /**
     * 攻击方参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle ATTACKER_LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "attacker_level_cap",
                    10, 1, 100);

    /**
     * 攻击方效果等级的上限
     * <p>默认 5，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle ATTACKER_AMPLIFIER_CAP =
            EnchantmentValues.define(VALUE_ID, "attacker_amplifier_cap",
                    5, 1, 100);

    /**
     * 每级的效果持续秒数
     * <p>默认 2.5，允许范围 0.1 ~ 120.0。</p>
     */
    private static final EnchantmentValues.Handle DURATION_SECONDS_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "duration_seconds_per_level",
                    2.5D, 0.1D, 120.0D);

    public EnchantmentGoldenVow() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingAttack(@NotNull LivingAttackEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();
        if (!(damageSource.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        Enchantment goldenVow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentGoldenVow.class);
        if (goldenVow == null) {
            return;
        }

        // 受击者视角
        // ⭐ v2.1：怪物附魔触发开关（受击者视角）
        if (!EnchantmentEventHandler.shouldBlockMobTrigger(victim, false)) {
            int victimLevel = 0;
            for (ItemStack armor : victim.getArmorSlots()) {
                if (!armor.isEmpty()) {
                    victimLevel += EnchantmentHelper.getItemEnchantmentLevel(goldenVow, armor);
                }
            }

            if (ConfigLoader.levelLimit) {
                victimLevel = Math.min(victimLevel, VICTIM_LEVEL_CAP.getInt());
            }

            if (victimLevel > 0) {
                int duration = (int) (DURATION_SECONDS_PER_LEVEL.get() * victimLevel * 20);
                victim.addEffect(new MobEffectInstance(
                        CarianStylePotion.GOLDEN_VOW.get(),
                        duration,
                        victimLevel - 1
                ));
            }
        }

        // 攻击者视角
        // ⭐ v2.1：怪物附魔触发开关（攻击者视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        int attackerLevel = 0;
        for (ItemStack armor : attacker.getArmorSlots()) {
            if (!armor.isEmpty()) {
                attackerLevel += EnchantmentHelper.getItemEnchantmentLevel(goldenVow, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            attackerLevel = Math.min(attackerLevel, ATTACKER_LEVEL_CAP.getInt());
        }

        if (attackerLevel > 0) {
            attackerLevel = Math.min(attackerLevel, ATTACKER_AMPLIFIER_CAP.getInt());

            int duration = (int) (DURATION_SECONDS_PER_LEVEL.get() * attackerLevel * 20);
            attacker.addEffect(new MobEffectInstance(
                    CarianStylePotion.GOLDEN_VOW.get(),
                    duration,
                    attackerLevel - 1
            ));
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((35 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }

    @Override
    protected boolean checkCompatibility(@NotNull Enchantment ench) {
        return super.checkCompatibility(ench) && !ench.equals(Enchantments.ALL_DAMAGE_PROTECTION);
    }
}
