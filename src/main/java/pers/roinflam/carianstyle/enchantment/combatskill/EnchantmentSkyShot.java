package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentEventHandler;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.tuning.EnchantmentValues;
import pers.roinflam.carianstyle.utils.Reference;
import pers.roinflam.carianstyle.visual.effect.CarianStyleCombatArtEffects;

import javax.annotation.Nonnull;

/**
 * 对空射击附魔
 * <p>
 * 射中比自己高出 5 格以上的生物时，这支箭的基础伤害变为 基础伤害 × (1 + 等级) + 目标当前生命 × 10%，
 * 射手获得 100 tick 的 2 级缓慢。
 * </p>
 * <p>v2.2：射手视角接入怪物附魔触发开关</p>
 * <p>
 * v2.3：接入对空射击特效（自更高处竖直贯下的箭光 + <b>目标高度处</b>的空爆环）。
 * 特效<b>只在高度差判定通过之后</b>才播——普通命中不该有这个演出。
 * </p>
 * <p>
 * 1.12.2 版：箭命中走 {@code ProjectileImpactEvent.Arrow}，射手是 {@code shootingEntity}，
 * 基础伤害是 {@code getDamage} / {@code setDamage}；命中事件在原版结算伤害之前发出，改了就生效。
 * </p>
 *
 * @author RoinFlam
 * @version 2.3
 */
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentSkyShot extends EnchantmentBase {

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
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();

        RayTraceResult hit = evt.getRayTraceResult();
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.ENTITY) {
            return;
        }

        if (arrow.shootingEntity == null) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        if (!(hit.entityHit instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase shooter = (EntityLivingBase) arrow.shootingEntity;

        // v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(shooter, false)) return;

        EntityLivingBase target = (EntityLivingBase) hit.entityHit;

        ItemStack heldItem = shooter.getHeldItem(EnumHand.MAIN_HAND);
        if (heldItem.isEmpty()) {
            return;
        }

        Enchantment skyShot = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSkyShot.class);
        if (skyShot == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(skyShot, heldItem);
        if (level <= 0) {
            return;
        }

        int effectiveLevel = level;
        effectiveLevel = applyLevelLimit(skyShot, effectiveLevel, LEVEL_CAP.getInt());

        double heightDifference = target.posY - shooter.posY;
        if (heightDifference < HEIGHT_THRESHOLD.get()) {
            return;
        }

        double baseDamage = arrow.getDamage();
        double bonusDamage1 = baseDamage * effectiveLevel;
        double bonusDamage2 = target.getHealth() * TARGET_HEALTH_DAMAGE.get();

        arrow.setDamage(baseDamage + bonusDamage1 + bonusDamage2);

        // v2.3：对空射击特效。
        // 必须放在高度差判定之后 —— 这个演出的全部语义就是「在空中把它打下来」。
        // 传入的是 target 本身（它此刻在空中），空爆环会画在它所处的高度而非地面
        if (shooter.world instanceof WorldServer) {
            CarianStyleCombatArtEffects.skyShot((WorldServer) shooter.world, shooter, target);
        }

        shooter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1));
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
