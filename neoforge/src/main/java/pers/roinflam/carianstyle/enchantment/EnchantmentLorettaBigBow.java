package pers.roinflam.carianstyle.enchantment;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import pers.roinflam.carianstyle.annotation.EnchantTarget;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
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
import pers.roinflam.carianstyle.base.entity.ArrowDamage;

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
        type = EnchantTarget.BOW,
        slots = {EquipmentSlot.MAINHAND},
        baseEnchantability = 25,
        levelMultiplier = 0
)
@EventBusSubscriber
public class EnchantmentLorettaBigBow extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.loretta_big_bow.desc，
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
        super(EnchantTarget.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
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

        // v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        EnchantmentBase lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentLevels.get(lorettaBigBow, heldItem);

        level = applyLevelLimit(lorettaBigBow, level, LEVEL_CAP.getInt());

        if (level > 0) {
            // 1.21.1 的力量到命中时才加，经 ArrowDamage 乘在含力量的箭伤上；刚进世界还没有目标
            ArrowDamage.scale(arrow, null, base -> base * ARROW_DAMAGE_MULTIPLIER.get());
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

        // v2.1：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(attacker, false)) return;

        ItemStack heldItem = attacker.getMainHandItem();
        if (heldItem.isEmpty()) {
            heldItem = attacker.getOffhandItem();
        }

        if (heldItem.isEmpty()) {
            return;
        }

        EnchantmentBase lorettaBigBow = EnchantmentRegistry.getEnchantmentByClass(EnchantmentLorettaBigBow.class);
        if (lorettaBigBow == null) {
            return;
        }

        int level = EnchantmentLevels.get(lorettaBigBow, heldItem);

        level = applyLevelLimit(lorettaBigBow, level, LEVEL_CAP.getInt());

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
        spawnBlastCloud(attacker.level(), arrow.getX(), arrow.getY(), arrow.getZ());
    }

    /**
     * 补一团大爆炸云
     * <p>问题：爆炸用的是 {@code ExplosionInteraction.NONE}（不破坏方块），原版客户端对这种爆炸
     * 只画一个小的 {@code EXPLOSION} 粒子，强度 2~4 的爆炸看上去只冒一小团烟。</p>
     * <p>做法：服务端在爆炸点再发一个 {@code EXPLOSION_EMITTER}（TNT 同款的大爆炸云），只是画面，
     * 伤害、击退、范围都不变。罗蕾塔的绝招也调这里。</p>
     */
    static void spawnBlastCloud(Level level, double x, double y, double z) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

}
