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
 * @version 2.0
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

        if (!RandomUtil.percentageChance(level * 2)) {
            return;
        }

        final int effectiveLevel = level;
        final float baseDamage = evt.getAmount();

        for (int x = -1; x < 2; x++) {
            for (int z = -1; z < 2; z++) {
                int delay = (int) (55 + x * 7.5 + z * 7.5);

                EntityGlintblades showBlade = new EntityGlintblades(attacker, victim).setDeadTick(delay);
                showBlade.setPos(
                        victim.getX() + x,
                        victim.getY() + 1,
                        victim.getZ() + z
                );
                victim.level().addFreshEntity(showBlade);

                new SynchronizationTask(delay) {
                    @Override
                    public void run() {
                        double posX = showBlade.getX();
                        double posY = showBlade.getY();
                        double posZ = showBlade.getZ();

                        EntityGlintblades attackBlade = new EntityGlintblades(attacker, victim);
                        attackBlade.setPos(posX, posY, posZ);

                        DamageSource magicDamage = attacker.damageSources().thrown(attackBlade, attacker);
                        DamageSourceUtil.setMagicDamage(magicDamage);
                        attackBlade.setDamageSource(magicDamage);
                        attackBlade.setDamage(baseDamage * effectiveLevel * 0.05f);
                        attackBlade.shoot(1);
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