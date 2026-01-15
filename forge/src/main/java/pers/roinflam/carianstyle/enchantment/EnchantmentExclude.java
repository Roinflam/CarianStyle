package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 排斥附魔
 *
 * 护腿附魔，受击时范围击退
 * 受到攻击时击退周围所有敌人
 * 范围 = 5 + (等级 - 1) × 0.75格
 */
@AutoRegisterEnchantment(
        id = "exclude",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentExclude extends EnchantmentBase {

    public EnchantmentExclude() {
        super(EnumEnchantmentType.ARMOR_LEGS, new EntityEquipmentSlot[]{EntityEquipmentSlot.LEGS});
    }

    /**
     * 受到攻击时击退周围敌人
     * 由于需要检查受击者的护甲，保留静态监听器
     * 使用HIGHEST优先级确保最早触发
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingAttack(@Nonnull LivingAttackEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须有攻击来源
        if (!(evt.getSource().getTrueSource() instanceof EntityLivingBase)) {
            return;
        }

        EntityLivingBase victim = evt.getEntityLiving();
        Enchantment exclude = EnchantmentRegistry.getEnchantmentByClass(EnchantmentExclude.class);

        if (exclude == null) {
            return;
        }

        // 从护甲获取附魔等级
        int totalLevel = 0;
        for (ItemStack armor : victim.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(exclude, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 计算击退范围
        double range = 5 + (totalLevel - 1) * 0.75;

        // 获取范围内的所有敌人
        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                (int) range,
                entity -> !entity.equals(victim)
        );

        // 击退所有敌人
        for (Entity entity : targets) {
            EntityLivingBase target = (EntityLivingBase) entity;

            // 计算击退方向（从受击者指向目标）
            double x = victim.posX - target.posX;
            double z = victim.posZ - target.posZ;

            // 设置攻击方向角度
            target.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) target.rotationYaw);

            // 击退目标
            target.knockBack(entity, 0.5f, x, z);
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}