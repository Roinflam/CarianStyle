package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
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
import pers.roinflam.carianstyle.tuning.EnchantmentValues;

/**
 * 洛蕾塔大弓附魔
 * <p>v2.1：EntityJoinLevel箭矢生成+ProjectileImpact命中两个入口接入怪物附魔触发开关</p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "loretta_big_bow",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.loretta_big_bow.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "loretta_big_bow";

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 箭矢基础伤害的倍率
     * <p>默认 1.5，允许范围 0.0 ~ 10.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    1.5D, 0.0D, 10.0D);

    /**
     * 爆炸强度
     * <p>默认 2.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH =
            EnchantmentValues.define(VALUE_ID, "explosion_strength",
                    2.0D, 0.0D, 20.0D);

    /**
     * 火箭矢的爆炸强度
     * <p>默认 3.0，允许范围 0.0 ~ 20.0。</p>
     */
    private static final EnchantmentValues.Handle EXPLOSION_STRENGTH_ON_FIRE =
            EnchantmentValues.define(VALUE_ID, "explosion_strength_on_fire",
                    3.0D, 0.0D, 20.0D);

    public EnchantmentLorettaBigBow() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢生成时增加伤害
     * <p>v2.1：射手视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onArrowJoinWorld(@NotNull EntityJoinLevelEvent evt) {
        if (evt.getLevel().isClientSide()) {
            return;
        }

        if (!(evt.getEntity() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity attacker)) {
            return;
        }

        // ⭐ v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaBigBow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }

        if (level > 0) {
            arrow.setBaseDamage(arrow.getBaseDamage() * ARROW_DAMAGE_MULTIPLIER.get());
        }
    }

    /**
     * 箭矢命中时爆炸
     * <p>v2.1：射手视角接入怪物附魔触发开关</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity attacker)) {
            return;
        }

        // ⭐ v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(lorettaBigBow, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, LEVEL_CAP.getInt());
        }

        if (level <= 0) {
            return;
        }

        float explosionStrength = (float) (arrow.getRemainingFireTicks() > 0
                ? EXPLOSION_STRENGTH_ON_FIRE.get() : EXPLOSION_STRENGTH.get());
        attacker.level().explode(
                attacker,
                arrow.getX(),
                arrow.getY(),
                arrow.getZ(),
                explosionStrength,
                false,
                net.minecraft.world.level.Level.ExplosionInteraction.NONE
        );
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (25 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
