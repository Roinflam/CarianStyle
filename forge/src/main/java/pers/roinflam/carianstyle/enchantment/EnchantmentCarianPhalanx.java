package pers.roinflam.carianstyle.enchantment;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import pers.roinflam.carianstyle.annotation.AutoRegisterEnchantment;
import pers.roinflam.carianstyle.annotation.EnchantmentRarity;
import pers.roinflam.carianstyle.base.enchantment.EnchantmentBase;
import pers.roinflam.carianstyle.config.ConfigLoader;
import pers.roinflam.carianstyle.annotation.registry.EnchantmentRegistry;
import pers.roinflam.carianstyle.entity.projectile.EntityGlintblades;
import pers.roinflam.carianstyle.utils.helper.task.SynchronizationTask;
import pers.roinflam.carianstyle.utils.java.random.RandomUtil;
import pers.roinflam.carianstyle.utils.util.DamageSourceUtil;

/**
 * 卡利亚方阵附魔
 * <p>
 * 弓箭伤害时概率生成魔法剑阵列攻击目标
 * 每把剑伤害 = 原伤害×等级×5%
 * </p>
 *
 * @author RoinFlam
 * @version 2.1
 */
@AutoRegisterEnchantment(
        id = "carian_phalanx",
        category = pers.roinflam.carianstyle.annotation.EnchantmentCategory.GENERAL,
        rarity = EnchantmentRarity.RARE,
        type = EnchantmentCategory.BOW,
        slots = {EquipmentSlot.MAINHAND},
        conflictsWith = {
                EnchantmentPyroxeneIce.class
        }
)
@Mod.EventBusSubscriber
public class EnchantmentCarianPhalanx extends EnchantmentBase {

    public EnchantmentCarianPhalanx() {
        super(EnchantmentCategory.BOW, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingDamage(@NotNull LivingDamageEvent evt) {
        if (evt.getEntity().level().isClientSide) {
            return;
        }

        DamageSource damageSource = evt.getSource();

        // 检查是否为弓箭伤害
        if (!(damageSource.getDirectEntity() instanceof AbstractArrow)) {
            return;
        }
        if (!(damageSource.getEntity() instanceof LivingEntity)) {
            return;
        }

        LivingEntity victim = evt.getEntity();
        LivingEntity attacker = (LivingEntity) damageSource.getEntity();

        ItemStack heldItem = attacker.getItemInHand(attacker.getUsedItemHand());
        if (heldItem.isEmpty()) {
            return;
        }

        // 获取附魔
        Enchantment carianPhalanx = EnchantmentRegistry.getEnchantmentByClass(EnchantmentCarianPhalanx.class);
        if (carianPhalanx == null) {
            return;
        }

        int level = EnchantmentHelper.getItemEnchantmentLevel(carianPhalanx, heldItem);

        if (ConfigLoader.levelLimit) {
            level = Math.min(level, 10);
        }

        if (level <= 0) {
            return;
        }

        // 触发概率：等级×2%
        if (!RandomUtil.percentageChance(level * 2)) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        // 生成3x3阵列（跳过中心）
        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                // 中心位置不生成
                if (x == 0 && z == 0) {
                    continue;
                }

                // 延迟时间：外围先，中心后
                int delay = (int) (55 + (Math.abs(x) + Math.abs(z)) * 7.5);

                // 显示用的剑（悬浮在受击者周围）
                EntityGlintblades showBlade = new EntityGlintblades(attacker, victim)
                        .setDeadTick(delay)
                        .setSize(1.0f);
                showBlade.setPos(
                        victim.getX() + x * 1.5,  // 间距1.5格
                        victim.getY() + 2,         // 高度2格
                        victim.getZ() + z * 1.5
                );
                victim.level().addFreshEntity(showBlade);

                final double finalPosX = showBlade.getX();
                final double finalPosY = showBlade.getY();
                final double finalPosZ = showBlade.getZ();

                // 延迟发射
                new SynchronizationTask(delay) {
                    @Override
                    public void run() {
                        // 检查目标有效性
                        if (!victim.isAlive() || victim.isRemoved()) {
                            return;
                        }

                        // 创建攻击剑
                        EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim)
                                .setSize(1.0f)
                                .setDamage(baseDamage * effectiveLevel * 0.05f)
                                .setDamageSource(attacker.damageSources().thrown(null, attacker))
                                .setTrackingStrength(0.2f)  // 较强追踪（阵列需要精确）
                                .setMaxLifetime(80);         // 4秒存活时间

                        DamageSourceUtil.setMagicDamage(attackBlade.getDamageSource());

                        attackBlade.setPos(finalPosX, finalPosY, finalPosZ);
                        attackBlade.shoot(1.5f);
                        victim.level().addFreshEntity(attackBlade);
                    }
                }.start();
            }
        }
    }

    @Override
    public int getMinCost(int enchantmentLevel) {
        return (int) ((20 + (enchantmentLevel - 1) * 10) * ConfigLoader.enchantingDifficulty);
    }

    @Override
    public int getMaxCost(int enchantmentLevel) {
        return getMinCost(enchantmentLevel) + 50;
    }
}