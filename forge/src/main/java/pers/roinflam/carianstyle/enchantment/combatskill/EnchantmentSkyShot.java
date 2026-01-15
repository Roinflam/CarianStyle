// 文件：EnchantmentSkyShot.java
// 路径：src/main/java/pers/roinflam/carianstyle/enchantment/combatskill/EnchantmentSkyShot.java
package pers.roinflam.carianstyle.enchantment.combatskill;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;

import javax.annotation.Nonnull;

/**
 * 对空射击附魔
 *
 * 效果：
 * - 射击比自己高至少5格的目标时触发
 * - 额外造成 100% × 等级 的伤害
 * - 额外造成目标当前生命值 × 10% 的伤害
 * - 触发后自身获得减速II效果，持续5秒
 * - 最大等级：3
 */
@AutoRegisterEnchantment(
        id = "sky_shot",
        category = EnchantmentCategory.COMBAT_SKILL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentSkyShot extends EnchantmentBase {

    /**
     * 高度差阈值（格数）
     */
    private static final double HEIGHT_THRESHOLD = 5.0;

    /**
     * 构造函数
     */
    public EnchantmentSkyShot() {
        // 弓附魔，主手装备
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢命中时触发：检查高度差并造成额外伤害
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        EntityArrow arrow = evt.getArrow();

        // 必须命中实体
        if (evt.getRayTraceResult().entityHit == null) {
            return;
        }

        // 必须有射手
        if (arrow.shootingEntity == null) {
            return;
        }

        // 射手必须是生物
        if (!(arrow.shootingEntity instanceof EntityLivingBase)) {
            return;
        }

        // 被击中的必须是生物
        if (!(evt.getRayTraceResult().entityHit instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase shooter = (EntityLivingBase) arrow.shootingEntity;
        EntityLivingBase target = (EntityLivingBase) evt.getRayTraceResult().entityHit;

        // 检查射手是否持有弓
        if (shooter.getHeldItem(shooter.getActiveHand()).isEmpty()) {
            return;
        }

        // 获取附魔实例
        Enchantment skyShot = EnchantmentRegistry.getEnchantmentByClass(EnchantmentSkyShot.class);
        if (skyShot == null) {
            return;
        }

        // 获取附魔等级
        int level = EnchantmentHelper.getEnchantmentLevel(
                skyShot,
                shooter.getHeldItem(shooter.getActiveHand()));

        if (level <= 0) {
            return;
        }

        // 手动应用等级限制
        int effectiveLevel = level;
        if (ConfigLoader.levelLimit) {
            effectiveLevel = Math.min(effectiveLevel, 10);
        }

        // 检查高度差：目标必须比射手高至少5格
        double heightDifference = target.posY - shooter.posY;
        if (heightDifference < HEIGHT_THRESHOLD) {
            return;
        }

        // 计算原始箭矢伤害
        double baseDamage = arrow.getDamage();

        // 额外伤害1：100% × 等级
        double bonusDamage1 = baseDamage * effectiveLevel;

        // 额外伤害2：目标当前生命值 × 10%
        double bonusDamage2 = target.getHealth() * 0.1;

        // 设置新的箭矢伤害
        arrow.setDamage(baseDamage + bonusDamage1 + bonusDamage2);

        // 给射手施加减速II效果，持续5秒（100 tick）
        shooter.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, 100, 1));
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        // RARE 的默认公式：10 + (level - 1) * 15
        return (int) ((10 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}