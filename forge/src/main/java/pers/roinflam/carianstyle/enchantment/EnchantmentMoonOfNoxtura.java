package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.enchantment.dead.EnchantmentAncientDragonLightning;
import pers.roinflam.carianstyle.enchantment.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.EntityUtil;

import java.util.List;

/**
 * 诺克斯之月附魔
 * <p>
 * 护甲附魔，夜间迷惑敌人
 * 夜间被怪物锁定时：
 * - 2.5%概率使怪物转移攻击目标到附近其他实体
 * </p>
 *
 * @author RoinFlam
 * @version 2.0
 */
@AutoRegisterEnchantment(
        id = "moon_of_noxtura",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.VERY_RARE,
        type = EnchantmentCategory.ARMOR,
        slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET},
        conflictsWith = {
                EnchantmentHealingByFire.class,
                EnchantmentShelterOfFire.class,
                EnchantmentPreciseLightning.class,
                EnchantmentAncientDragonLightning.class
        },
        forceTreasure = true
)
@Mod.EventBusSubscriber
public class EnchantmentMoonOfNoxtura extends EnchantmentBase {

    public EnchantmentMoonOfNoxtura() {
        super(EnchantmentCategory.ARMOR, new EquipmentSlot[]{
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        });
    }

    /**
     * 夜间被怪物锁定时概率转移仇恨
     * 注意：1.20.1移除了LivingSetAttackTargetEvent，改用Tick检查
     */
    @SubscribeEvent
    public static void onServerTick(@NotNull TickEvent.ServerTickEvent evt) {
        if (evt.phase != TickEvent.Phase.END) {
            return;
        }

        // 每20tick检查一次（1秒）
        if (evt.getServer().getTickCount() % 20 != 0) {
            return;
        }

        Enchantment moonOfNoxtura = EnchantmentRegistry.getEnchantmentByClass(EnchantmentMoonOfNoxtura.class);
        if (moonOfNoxtura == null) {
            return;
        }

        // 遍历所有世界的所有实体
        evt.getServer().getAllLevels().forEach(level -> {
            if (level.isDay()) {
                return;
            }

            level.getAllEntities().forEach(entity -> {
                if (!(entity instanceof Mob mob)) {
                    return;
                }

                LivingEntity target = mob.getTarget();
                if (target == null) {
                    return;
                }

                // 从目标的护甲累加附魔等级
                int totalLevel = 0;
                for (ItemStack armor : target.getArmorSlots()) {
                    if (!armor.isEmpty()) {
                        totalLevel += EnchantmentHelper.getItemEnchantmentLevel(moonOfNoxtura, armor);
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
                double distance = mob.distanceTo(target);
                List<LivingEntity> entities = EntityUtil.getNearbyEntities(
                        LivingEntity.class,
                        mob,
                        (int) distance,
                        e -> e.getClass() != mob.getClass()
                                && mob.hasLineOfSight(e)
                                && !e.equals(mob)
                                && !e.equals(target)
                );

                if (!entities.isEmpty()) {
                    // 随机选择一个新目标
                    mob.setTarget(entities.get(RandomUtil.getInt(0, entities.size() - 1)));
                }
            });
        });
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