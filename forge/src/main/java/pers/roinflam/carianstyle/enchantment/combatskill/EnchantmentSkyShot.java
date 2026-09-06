package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

/**
 * 对空射击附魔
 * <p>v2.2：射手视角接入怪物附魔触发开关</p>
 * <p>
 * v2.3：接入对空射击特效（自更高处竖直贯下的箭光 + <b>目标高度处</b>的空爆环）。
 * 特效<b>只在高度差判定通过之后</b>才播——普通命中不该有这个演出。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber
public class EnchantmentSkyShot extends EnchantmentBase {

    // ==================== 可调数值（config/carianstyle/enchantment_values.json）====================
    // 句柄存为 static final，读取时是一次字段访问，可安全用在伤害/tick 路径上。
    // ⚠ 修改数值后请自行同步修改语言文件中的 enchantment.carianstyle.sky_shot.desc，
    //   否则玩家看到的描述会与实际效果不符。

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "sky_shot";

    /**
     * 触发所需的目标高度差（格）
     * <p>默认 5.0，允许范围 0.0 ~ 128.0。</p>
     */
    private static final EnchantmentValues.Handle HEIGHT_THRESHOLD =
            EnchantmentValues.define(VALUE_ID, "height_threshold_blocks",
                    5.0D, 0.0D, 128.0D);

    /**
     * 参与计算的等级上限
     * <p>默认 10，允许范围 1 ~ 100。</p>
     */
    private static final EnchantmentValues.Handle LEVEL_CAP =
            EnchantmentValues.define(VALUE_ID, "level_cap",
                    10, 1, 100);

    /**
     * 基于目标当前生命的额外伤害占比
     * <p>默认 0.1，允许范围 0.0 ~ 1.0。</p>
     */
    private static final EnchantmentValues.Handle TARGET_HEALTH_DAMAGE =
            EnchantmentValues.define(VALUE_ID, "target_health_damage",
                    0.1D, 0.0D, 1.0D);

    public EnchantmentSkyShot() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@NotNull ProjectileImpactEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        if (!(evt.getProjectile() instanceof AbstractArrow)) {
            return;
        }

        AbstractArrow arrow = (AbstractArrow) evt.getProjectile();

        if (evt.getRayTraceResult().getType() != net.minecraft.world.phys.HitResult.Type.ENTITY) {
            return;
        }

        net.minecraft.world.phys.EntityHitResult entityHit = (net.minecraft.world.phys.EntityHitResult) evt.getRayTraceResult();

        if (arrow.getOwner() == null) {
            return;
        }

        if (!(arrow.getOwner() instanceof LivingEntity)) {
            return;
        }

        if (!(entityHit.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity shooter = (LivingEntity) arrow.getOwner();

        // ⭐ v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(shooter, false)) return;

        LivingEntity target = (LivingEntity) entityHit.getEntity();

        ItemStack heldItem = shooter.getItemInHand(InteractionHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment skyShot = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSkyShot.class);
        if (skyShot == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(skyShot, heldItem);
        if (level <= 0) {
            return;
        }

        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, LEVEL_CAP.getInt());
        }

        double heightDifference = target.getY() - shooter.getY();
        if (heightDifference < HEIGHT_THRESHOLD.get()) {
            return;
        }

        double baseDamage = arrow.getBaseDamage();
        double bonusDamage1 = baseDamage * effectiveLevel;
        double bonusDamage2 = target.getHealth() * TARGET_HEALTH_DAMAGE.get();

        arrow.setBaseDamage(baseDamage + bonusDamage1 + bonusDamage2);

        // ⭐ v2.3：对空射击特效。
        // 必须放在高度差判定之后 —— 这个演出的全部语义就是「在空中把它打下来」。
        // 传入的是 target 本身（它此刻在空中），空爆环会画在它所处的高度而非地面
        if (shooter.level() instanceof ServerLevel serverLevel) {
            CarianStyleCombatArtEffects.skyShot(serverLevel, shooter, target);
        }

        shooter.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
