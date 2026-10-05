package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.dynamicattr.ClientSyncEffectHelper;
import pers.roinflam.carianstyle.dynamicattr.DynamicAttributeManager;
import pers.roinflam.carianstyle.dynamicattr.dynamiceffect.DynamicAttributes;
import pers.roinflam.carianstyle.source.NewDamageSource;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.helper.dot.DamageOverTimeManager;

/**
 * 癫火附魔
 * <p>v3.1：LivingHurt攻击者视角入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 3.1
 */
@AutoRegisterEnchantment(
        id = "epilepsy_fire",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.WEAPON,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentEpilepsyFire extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.epilepsy_fire.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "epilepsy_fire";

    /**
     * 燃烧视觉效果的持续时间（tick）
     * <p>默认 65，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle BURN_VISUAL_DURATION =
            EnchantmentValues.define(VALUE_ID, "burn_visual_duration",
                    65, 1, 1200);

    /**
     * 持续伤害的总时长（tick）
     * <p>默认 60，允许范围 1 ~ 1200。</p>
     */
    private static final EnchantmentValues.Handle DAMAGE_TICKS =
            EnchantmentValues.define(VALUE_ID, "damage_ticks",
                    60, 1, 1200);

    /**
     * 持续伤害的起始延迟（tick）
     * <p>默认 5，允许范围 0 ~ 200。</p>
     */
    private static final EnchantmentValues.Handle DOT_DELAY =
            EnchantmentValues.define(VALUE_ID, "dot_delay",
                    5, 0, 200);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 自身承受的总伤害占最大生命的比例
     * <p>默认 0.2，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle SELF_DAMAGE_RATIO =
            EnchantmentValues.define(VALUE_ID, "self_damage_ratio",
                    0.2D, 0.0D, 1.0D);

    /**
     * 每级施加给目标的伤害倍率（以自身承受量为基数）
     * <p>默认 0.1，允许范围 0.0 ~ 2.0。</p>
     */
    private static final EnchantmentValues.Handle VICTIM_DAMAGE_PER_LEVEL =
            EnchantmentValues.define(VALUE_ID, "victim_damage_per_level",
                    0.1D, 0.0D, 2.0D);

    public EnchantmentEpilepsyFire() {
        super(EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(@NotNull LivingHurtEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getSource().getDirectEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) evt.getSource().getDirectEntity();

        // ⭐ v3.1：怪物附魔触发开关（攻击者视角，自损+对敌DoT）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        Enchantment epilepsyFire = EnchantmentRegistry.getEnchantmentByClass(EnchantmentEpilepsyFire.class);
        if (epilepsyFire == null) {
            return;
        }

        ItemStack heldItem = attacker.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(epilepsyFire, heldItem);
        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }
        if (level <= 0) {
            return;
        }

        if (!isFullyCharged(attacker)) {
            return;
        }

        final int effectiveLevel = level;

        DynamicAttributeManager.apply(attacker,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION.getInt(), 0));
        ClientSyncEffectHelper.onAttributeApplied(attacker, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        // 自损：被反噬耗死时致死一击走原版伤害管线（见 DamageOverTimeManager v1.2）
        float attackerDmgPerTick = attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get() / DAMAGE_TICKS.getInt();
        DamageOverTimeManager.applySelfLinear(
                attacker, attackerDmgPerTick, DAMAGE_TICKS.getInt(), DOT_DELAY.getInt(),
                NewDamageSource.epilepsyFire(attacker.level())
        );

        DynamicAttributeManager.apply(victim,
                DynamicAttributes.EPILEPSY_FIRE_BURNING.createInstance(BURN_VISUAL_DURATION.getInt(), 0));
        ClientSyncEffectHelper.onAttributeApplied(victim, DynamicAttributes.EPILEPSY_FIRE_BURNING);

        float victimDmgPerTick = attacker.getMaxHealth() * (float) SELF_DAMAGE_RATIO.get()
                * effectiveLevel * (float) VICTIM_DAMAGE_PER_LEVEL.get() / DAMAGE_TICKS.getInt();
        DamageOverTimeManager.applyLinear(
                victim, victimDmgPerTick, DAMAGE_TICKS.getInt(), DOT_DELAY.getInt(),
                NewDamageSource.epilepsyFire(victim.level()), true
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((5 + (enchantmentLevel - 1) * 25) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
