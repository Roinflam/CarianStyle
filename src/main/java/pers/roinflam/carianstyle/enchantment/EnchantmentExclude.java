package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
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
 * 受到攻击时击退周围所有生物
 * 范围 = 5 + (等级 - 1) × 0.75 格，至多 10 格，一次至多 20 个
 * <p>
 * 以前半径没有上限、目标数不限，传给查询前还会 (int) 取整（等级和 2 时 5.75 变 5）。
 * </p>
 */
@AutoRegisterEnchantment(
        id = "exclude",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE
)
@Mod.EventBusSubscriber
public class EnchantmentExclude extends EnchantmentBase {

    /** 作用半径上限（格） */
    private static final double MAX_SEARCH_RADIUS = 10.0D;
    /** 单次触发至多击退的目标数 */
    private static final int MAX_TARGETS = 20;
    /** 参与计算的等级上限（开了等级上限时生效） */
    private static final int LEVEL_CAP = 10;
    /** 基础作用半径（格） */
    private static final double BASE_RANGE = 5.0D;
    /** 每超过一级增加的作用半径（格） */
    private static final double RANGE_PER_LEVEL = 0.75D;
    /** 击退强度 */
    private static final float KNOCKBACK_STRENGTH = 0.5F;

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
        if (shouldBlockMobTrigger(victim, false)) {
            return;
        }
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

        totalLevel = capLevel(totalLevel, LEVEL_CAP);

        if (totalLevel <= 0) {
            return;
        }

        // 计算击退范围（保留小数，至多 10 格）
        double range = Math.min(BASE_RANGE + (totalLevel - 1) * RANGE_PER_LEVEL, MAX_SEARCH_RADIUS);

        // 获取范围内的所有生物
        List<EntityLivingBase> targets = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                victim,
                range,
                entity -> !entity.equals(victim)
        );

        // 击退，至多 20 个
        int hitCount = 0;
        for (EntityLivingBase target : targets) {
            if (hitCount >= MAX_TARGETS) {
                break;
            }

            // 计算击退方向（从受击者指向目标）
            double x = victim.posX - target.posX;
            double z = victim.posZ - target.posZ;

            // 设置攻击方向角度
            target.attackedAtYaw = (float) (MathHelper.atan2(z, x) * (180D / Math.PI) - (double) target.rotationYaw);

            // 击退目标
            target.knockBack(target, KNOCKBACK_STRENGTH, x, z);
            hitCount++;
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) ((25 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }
}