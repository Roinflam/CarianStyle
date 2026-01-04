package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.projectile.EntityArrow;
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
import pers.roinflam.carianstyle.init.CarianStylePotion;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 龙息腐败附魔
 *
 * 弓箭附魔，箭矢落地时对范围内敌人施加猩红腐败
 * 范围 = 等级 × 2格
 * 猩红腐败：持续时间 = 等级 × 5秒，等级 = 附魔等级 - 1
 */
@AutoRegisterEnchantment(
        id = "dragon_breath_corruption",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentDragonBreathCorruption extends EnchantmentBase {

    public EnchantmentDragonBreathCorruption() {
        super(EnumEnchantmentType.BOW, new EntityEquipmentSlot[]{EntityEquipmentSlot.MAINHAND});
    }

    /**
     * 箭矢落地时对周围敌人施加猩红腐败
     * 注意：ProjectileImpactEvent基类无模板方法，保留静态监听器
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onProjectileImpact_Arrow(@Nonnull ProjectileImpactEvent.Arrow evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有射击者且未击中实体（落地）
        if (evt.getArrow().shootingEntity == null || evt.getRayTraceResult().entityHit != null) {
            return;
        }

        EntityArrow arrow = evt.getArrow();
        EntityLivingBase attacker = (EntityLivingBase) arrow.shootingEntity;
        Enchantment dragonBreath = EnchantmentRegistry.getEnchantmentByClass(EnchantmentDragonBreathCorruption.class);

        if (dragonBreath == null) {
            return;
        }

        if (attacker.getHeldItem(attacker.getActiveHand()).isEmpty()) {
            return;
        }

        int level = EnchantmentHelper.getEnchantmentLevel(
                dragonBreath,
                attacker.getHeldItem(attacker.getActiveHand())
        );

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 获取范围内的所有实体
        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                arrow,
                level * 2
        );

        // 对所有目标施加猩红腐败
        for (EntityLivingBase target : targets) {
            target.addPotionEffect(new PotionEffect(
                    CarianStylePotion.SCARLET_ROT,
                    level * 5 * 20,  // 持续时间：等级 × 5秒
                    level - 1        // 效果等级：附魔等级 - 1
            ));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 15) * ConfigLoader.enchantingDifficulty);
    }
}