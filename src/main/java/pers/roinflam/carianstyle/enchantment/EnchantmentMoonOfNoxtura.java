package pers.roinflam.carianstyle.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.EnumEnchantmentType;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentCategory;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 诺克斯之月附魔
 *
 * 护甲附魔，夜间迷惑敌人
 * 夜间被怪物锁定时：
 * - 2.5%概率使怪物转移攻击目标到附近其他实体
 */
@AutoRegisterEnchantment(
        id = "moon_of_noxtura",
        category = EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE
)
@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {

    public EnchantmentMoonOfNoxtura() {
        super(EnumEnchantmentType.ARMOR, new EntityEquipmentSlot[]{
                EntityEquipmentSlot.HEAD,
                EntityEquipmentSlot.CHEST,
                EntityEquipmentSlot.LEGS,
                EntityEquipmentSlot.FEET
        });
    }

    /**
     * 被怪物锁定时概率转移仇恨
     * 由于 LivingSetAttackTargetEvent 没有模板方法，且需要累加护甲等级，保留静态监听器
     */
    @SubscribeEvent
    public static void onLivingSetAttackTarget(@Nonnull LivingSetAttackTargetEvent evt) {
        if (evt.getEntity().world.isRemote) {
            return;
        }

        // 必须是夜间
        if (evt.getEntity().world.isDaytime()) {
            return;
        }

        // 必须是EntityLiving锁定目标
        if (!(evt.getEntityLiving() instanceof EntityLiving) || evt.getTarget() == null) {
            return;
        }

        EntityLiving attacker = (EntityLiving) evt.getEntityLiving();
        EntityLivingBase target = evt.getTarget();

        Enchantment moonOfNoxtura = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMoonOfNoxtura.class);
        if (moonOfNoxtura == null) {
            return;
        }

        // 从目标的护甲累加附魔等级
        int totalLevel = 0;
        for (ItemStack armor : target.getArmorInventoryList()) {
            if (!armor.isEmpty()) {
                totalLevel += EnchantmentHelper.getEnchantmentLevel(moonOfNoxtura, armor);
            }
        }

        if (ConfigLoader.levelLimit) {
            totalLevel = Math.min(totalLevel, 10);
        }

        if (totalLevel <= 0) {
            return;
        }

        // 2.5%概率触发
        if (!RandomUtil.percentageChance(2.5)) {
            return;
        }

        // 获取攻击者与目标之间距离内的其他实体
        double distance = attacker.getDistance(target);
        List<EntityLivingBase> entities = EntityUtil.getNearbyEntities(
                EntityLivingBase.class,
                attacker,
                (int) distance,
                entity -> entity.getClass() != attacker.getClass()
                        && attacker.canEntityBeSeen(entity)
                        && !entity.equals(attacker)
                        && !entity.equals(target)
        );

        if (!entities.isEmpty()) {
            // 随机选择一个新目标
            attacker.setAttackTarget(entities.get(RandomUtil.getInt(0, entities.size() - 1)));
        }
    }

    @Override
    public int getMinEnchantability(int enchantmentLevel) {
        return (int) (35 * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public boolean canApplyTogether(@Nonnull Enchantment ench) {
        return !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentHealingByFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentShelterOfFire.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentPreciseLightning.class))
                && !ench.equals(EnchantmentRegistry.getEnchantmentByClass(EnchantmentAncientDragonLightning.class));
    }

    @Override
    public boolean isTreasureEnchantment() {
        return true;
    }
}