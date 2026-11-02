package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Enchantments;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
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
import pers.roinflam.carianstyle.utils.util.EntityLivingUtil;

import javax.annotation.Nonnull;

/**
 * 连射附魔
 * <p>
 * 拉弓时每 tick 额外推进 4 次拉弓进度；射出的箭命中实体时伤害 ×50%。
 * </p>
 * <p>v2.2：ProjectileImpact射手+LivingTickEvent入口接入怪物附魔触发开关</p>
 * <p>
 * 1.12.2 版：箭命中走 {@code ProjectileImpactEvent.Arrow}，射手是 {@code shootingEntity}；
 * 生物更新是 {@code LivingUpdateEvent}；1.12.2 没有弩，只认弓。
 * </p>
 *
 * @author RoinFlam
 * @version 2.2
 */
@AutoRegisterEnchantment(
        id = "continuous_shooting",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnumEnchantmentType.BOW,
        slots = {EntityEquipmentSlot.MAINHAND}
)
@Mod.EventBusSubscriber(modid = Reference.MOD_ID)
public class EnchantmentContinuousShooting extends EnchantmentBase {

    /** 本附魔在数值配置文件中的分组键 */
    private static final String VALUE_ID = "continuous_shooting";

    /**
     * 连射箭矢的伤害倍率
     * <p>默认 0.5，允许范围 0.0 ~ 5.0。</p>
     */
    private static final EnchantmentValues.Handle ARROW_DAMAGE_MULTIPLIER =
            EnchantmentValues.define(VALUE_ID, "arrow_damage_multiplier",
                    0.5D, 0.0D, 5.0D);

    /**
     * 每次触发额外射出的箭矢数
     * <p>默认 4，允许范围 1 ~ 32。</p>
     */
    private static final EnchantmentValues.Handle EXTRA_ARROWS =
            EnchantmentValues.define(VALUE_ID, "extra_arrows",
                    4, 1, 32);

    private static volatile Enchantment cachedEnchantment = null;
    private static volatile boolean cacheAttempted = false;

    public EnchantmentContinuousShooting() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    private static Enchantment getCachedEnchantment() {
        if (!cacheAttempted) {
            cachedEnchantment = EnchantmentRegistry.getEnchantmentByClass(EnchantmentContinuousShooting.class);
            cacheAttempted = true;
        }
        return cachedEnchantment;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(@Nonnull ProjectileImpactEvent.Arrow evt) {
        EntityArrow arrow = evt.getArrow();

        if (arrow.world.isRemote) {
            return;
        }

        RayTraceResult hitResult = evt.getRayTraceResult();
        if (hitResult == null || hitResult.entityHit == null) {
            return;
        }

        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }
        EntityLivingBase shooter = (EntityLivingBase) arrow.shootingEntity;

        // v2.2：怪物附魔触发开关（射手视角）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(shooter, false)) return;

        Enchantment continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        ItemStack heldItem = shooter.getHeldItemMainhand();
        if (heldItem.isEmpty() || !heldItem.isItemEnchanted()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(continuousShooting, heldItem);

        if (level > 0) {
            arrow.setDamage(arrow.getDamage() * ARROW_DAMAGE_MULTIPLIER.get());
        }
    }

    @SubscribeEvent
    public static void onLivingUpdate(@Nonnull LivingEvent.LivingUpdateEvent evt) {
        EntityLivingBase entity = evt.getEntityLiving();

        if (!entity.isHandActive()) {
            return;
        }

        // v2.2：怪物附魔触发开关（持有者视角，加速拉弓）
        if (EnchantmentEventHandler.shouldBlockMobTrigger(entity, false)) return;

        ItemStack heldItem = entity.getHeldItem(entity.getActiveHand());
        if (heldItem.isEmpty()) {
            return;
        }

        // 1.12.2 版：没有弩，只认弓
        if (!(heldItem.getItem() instanceof ItemBow)) {
            return;
        }

        if (!heldItem.isItemEnchanted()) {
            return;
        }

        Enchantment continuousShooting = getCachedEnchantment();
        if (continuousShooting == null) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(continuousShooting, heldItem);

        if (level > 0) {
            for (int i = 0; i < EXTRA_ARROWS.getInt(); i++) {
                EntityLivingUtil.updateHeld(entity);
            }
        }
    }

    @Override
    protected boolean checkCompatibility(@Nonnull Enchantment ench) {
        if (ench == Enchantments.INFINITY) {
            return false;
        }
        return super.checkCompatibility(ench);
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}
